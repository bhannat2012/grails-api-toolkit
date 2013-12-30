package net.nosegrind.apitoolkit

import grails.converters.JSON
import grails.converters.XML
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.lang.reflect.Method
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import org.codehaus.groovy.grails.validation.routines.UrlValidator
import org.springframework.web.context.request.RequestContextHolder as RCH


import net.nosegrind.apitoolkit.*


class ApiToolkitService{

	def grailsApplication
	def springSecurityService
	def apiCacheService

	static transactional = false

	Long responseCode
	String responseMessage
	
	def getRequest(){
		return RCH.currentRequestAttributes().currentRequest
	}

	def getResponse(){
		return RCH.currentRequestAttributes().currentResponse
	}

	def getParams(){
		def params = RCH.currentRequestAttributes().params
		def request = getRequest()
		def json = request.JSON
		json.each() { key,value ->
			params[key] = value
		}
		return params
	}
	
	// api call now needs to detect request method and see if it matches anno request method
	boolean isApiCall(){
		def request = getRequest()
		def params = getParams()
		def queryString = request.'javax.servlet.forward.query_string'
		
		def uri
		if(request.isRedirected()){
			if(params.action=='index'){
				uri = (queryString)?request.forwardURI+'?'+queryString:request.forwardURI+'/'+params.action
			}else{
				uri = (queryString)?request.forwardURI+'?'+queryString:request.forwardURI
			}
		}else{
			uri = (queryString)?request.forwardURI+'?'+queryString:request.forwardURI
		}
		
		def api
		if(grailsApplication.config.grails.app.context=='/'){
			api = "/${grailsApplication.config.apitoolkit.apiName}/${grailsApplication.metadata['app.version']}/"
		}else if(grailsApplication.config?.grails?.app?.context){
			api = "${grailsApplication.config.grails.app.context}/${grailsApplication.config.apitoolkit.apiName}/${grailsApplication.metadata['app.version']}/"
		}else if(!grailsApplication.config?.grails?.app?.context){
			api = "/${grailsApplication.metadata['app.name']}/${grailsApplication.config.apitoolkit.apiName}/${grailsApplication.metadata['app.version']}/"
		}
		api += (params?.format)?"${params.format}/${params.controller}/${params.action}":"JSON/${params.controller}/${params.action}"
		api += (params.id)?"/${params.id}":""
		api += (queryString)?"?${queryString}":""

		//println("${uri}==${api}")
		return uri==api
	}

	boolean isRequestMatch(String protocol){
		def request = getRequest()
		return request.method.toString()==protocol.toString()
	}
	
	// true=primary
	// false=foreign
	Integer getKey(String key){
		switch(key){
			case'FKEY':
				return 2
				break
			case 'PKEY':
				return 1
				break
			default:
				return 0
		}
	}
	
	/*
	 * Which annos declare this KEY as being 'received'.
	 * Check first in own controller then walk all others
	 */
	String createLinkRelationships(String paramType,String name,String controllername){
		def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', controllername)
		//def methods = controller?.getClazz().metaClass.methods*.name.sort().unique()
		for (Method method : controller.getClazz().getMethods()){
				if(method.isAnnotationPresent(Api)) {}
		}
	}
	
	Map formatModel(Object data){
		def newMap = [:]
		if(data && (!data?.response && !data?.metaClass && !data?.params)){
			data.each{ key, value ->
				if(value){
					println(value)
					if(grailsApplication.isDomainClass(value.getClass())){
						newMap[key]=value
					}else{
						if(value in java.util.Collection){
							if(value?.size()>0){
								if(grailsApplication.isDomainClass(value[0].getClass())){
									value.each{ k,v ->
										newMap[key][v.id]= v
									}
								}else{
									value = formatModel(value)
									newMap[key]= value
								}
							}
						}else{
							newMap[key]=value.toString()
						}
					}
				}else{
					println("no value")
				}
			}
		}
		return newMap
	}


	
	boolean validateUrl(String url){
		String[] schemes = ["http","https"]
		UrlValidator urlValidator = new UrlValidator(schemes)
		return urlValidator.isValid(url)
	}
	
	Boolean checkHookAuthority(ArrayList roles){
		if (springSecurityService.isLoggedIn()){
			def userRoles = springSecurityService.getPrincipal().getAuthorities()
			if(userRoles){
				if(userRoles.intersect(roles)){
					return true
				}
			}
		}
		return false
	}
	
	boolean checkAuthority(ArrayList role){
		List roles = role as List
		if(roles.size()>0 && roles[0].trim()){
			def roles2 = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.authority.className).clazz.list().authority
			def finalRoles
			def userRoles
			if (springSecurityService.isLoggedIn()){
				userRoles = springSecurityService.getPrincipal().getAuthorities()
			}
			
			if(userRoles){
				def temp = roles2.intersect(roles as Set)
				finalRoles = temp.intersect(userRoles)
				if(finalRoles){
					return true
				}else{
					return false
				}
			}else{
				return false
			}
		}else{
			return false
		}
	}
	
	void postData(String service, Map data, String state) {
		send(data, state, service)
	}
	
	void postData(String service, Object data, String state) {
		data = formatDomainObject(data)
		send(data, state, service)
	}
	
	private boolean send(Map data, String state, String service) {
println("send : ${data}")
		def hooks = grailsApplication.getClassForName(grailsApplication.config.apitoolkit.domain).findAll("from Hook where service='${service}'")

		hooks.each { hook ->
			String format = hook.format.toLowerCase()
			if(hook.attempts>=grailsApplication.config.apitoolkit.attempts){
				data = 	[message:'Number of attempts exceeded. Please reset hook via web interface']
			}
			String hookData
			
			try{
				def conn = hook.url.toURL().openConnection()
				conn.setRequestMethod("POST")
				conn.doOutput = true
				def queryString = []
				switch(format){
					case 'xml':
						hookData = (data as XML).toString()
						queryString << "state=${state}&xml=${hookData}"
						break
					case 'json':
					default:
						hookData = (data as JSON).toString()
println("hookdata :${hookData}")
						queryString << "state=${state}&json=${hookData}"
						break
				}
				def writer = new OutputStreamWriter(conn.outputStream)
				writer.write(queryString)
				writer.flush()
				writer.close()
				conn.connect()
				if(conn.content.text!='connected'){
					hook.attempts+=1
					hook.save(flush: true)
					log.info("[Hook] HookService : No Url ${hook.url} found")
				}
			}catch(Exception e){
				hook.attempts+=1
				hook.save(flush: true)
				log.info("[Hook] HookService : " + e)
			}
		}
	}
}