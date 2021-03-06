package net.nosegrind.apitoolkit

import grails.converters.JSON
import grails.converters.XML
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.lang.reflect.Method
import org.codehaus.groovy.grails.commons.DefaultGrailsControllerClass
import java.util.ArrayList
import java.util.HashSet
import java.util.Map

import org.codehaus.groovy.grails.validation.routines.UrlValidator
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH

import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes

import net.nosegrind.apitoolkit.*

import org.springframework.ui.ModelMap

class ApiToolkitService{

	def grailsApplication
	def springSecurityService
	def apiCacheService
	ApiStatuses error = new ApiStatuses()
	
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
		def type = ['XML':'text/html','JSON':'application/json','HTML':'application/xml'].findAll{ request.getHeader('Content-Type')?.startsWith(it.getValue()) }

		if(grailsApplication.config.grails.app.context=='/'){
			api = "/${grailsApplication.config.apitoolkit.apiName}_${grailsApplication.metadata['app.version']}/"
		}else if(grailsApplication.config?.grails?.app?.context){
			api = "${grailsApplication.config.grails.app.context}/${grailsApplication.config.apitoolkit.apiName}_${grailsApplication.metadata['app.version']}/"
		}else if(!grailsApplication.config?.grails?.app?.context){
			api = "/${grailsApplication.metadata['app.name']}/${grailsApplication.config.apitoolkit.apiName}_${grailsApplication.metadata['app.version']}/"
		}

		api += "${params.controller}/${params.action}"
		api += (params.id)?"/${params.id}":""
		api += (queryString)?"?${queryString}":""

		return uri==api
	}

	boolean isRequestMatch(List protocol){
		def request = getRequest()
		String method = net.nosegrind.apitoolkit.Method["${request.method.toString()}"].toString()
		if(protocol.contains(method)){
			return true
		}else{
			return false
		}
		return 
	}
	
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
	
	
	boolean validateUrl(String url){
		String[] schemes = ["http","https"]
		UrlValidator urlValidator = new UrlValidator(schemes)
		return urlValidator.isValid(url)
	}
	
	boolean checkHookAuthority(ArrayList roles){
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
	
	private boolean checkDocAuthority(HashSet role){
		List roles = role as List
		if(roles.size()>0 && roles[0].trim()){
			def roles2 = grailsApplication.getDomainClass(grailsApplication.config.grails.plugins.springsecurity.authority.className).clazz.list().authority
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
			return true
		}
	}
	
	void callHook(String service, Map data, String state) {
		send(data, state, service)
	}
	
	void callHook(String service, Object data, String state) {
		data = formatDomainObject(data)
		send(data, state, service)
	}
	
	boolean methodCheck(List roles){
		def optionalMethods = ['OPTIONS','HEAD']
		def requiredMethods = ['GET','POST','PUT','DELETE','PATCH','TRACE']
		
		def temp = roles.removeAll(optionalMethods)
		if(requiredMethods.contains(temp)){
			if(temp.size()>1){
				// ERROR: too many non-optional methods; only one is permitted
				return false
			}
		}else{
			// ERROR: unrecognized method
			return false
		}
		return true
	}
	
	private boolean send(Map data, String state, String service) {
		def hooks = grailsApplication.getClassForName(grailsApplication.config.apitoolkit.domain).findAll("from Hook where service='${service}/${state}'")
		def cache = apiCacheService.getApiCache(service)
		hooks.each { hook ->
			// get cache and check each users authority for hook
			def userRoles = []
			def authorities = hook.user.getAuthorities()
			authorities.each{
				userRoles += it.authority
			}
			def roles= cache["${state}"]['hookRoles']
			def temp = roles.intersect(userRoles)

			if(temp.size()>0){
				String format = hook.format.toLowerCase()
				if(hook.attempts>=grailsApplication.config.apitoolkit.attempts){
					data = 	[message:'Number of attempts exceeded. Please reset hook via web interface']
				}
				String hookData
	
				try{
					def url = new URL(hook.callback)
					def conn = url.openConnection()
					conn.setRequestMethod("POST")
					conn.setRequestProperty("User-Agent",'Mozilla/5.0')
					conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
					conn.setDoOutput(true)
					def queryString = []
					switch(format){
						case 'xml':
							hookData = (data as XML).toString()
							queryString << "state=${state}&xml=${hookData}"
							break
						case 'json':
						default:
							hookData = (data as JSON).toString()
							queryString << "state=${state}&json=${hookData}"
							break
					}
					
					def writer = new OutputStreamWriter(conn.getOutputStream())
					writer.write(queryString)
					writer.flush()
					writer.close()
					String output = conn.content.text
					conn.connect()
				}catch(Exception e){
					// ignore missing GSP/JSP exception
					if(!(e in java.io.FileNotFoundException)){
						hook.attempts+=1
						hook.save()
						log.info("[Hook] net.nosegrind.apitoolkit.ApiToolkitService : " + e)
					}
				}
			}else{
				hook.delete(flush:true)
			}
		}
	}
	
	boolean isRequestRedirected(){
		if(request.getAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED) != null){
			return true
		}else{
			return false
		}
	}
	
	List getRedirectParams(){
		// params.controller = temp[0]
		// params.action = temp[1]
		def uri = SCH.servletContext.getControllerActionUri(request)
		return uri[1..(uri.size()-1)].split('/')

	}
	
	private ArrayList processDocValues(HashSet value){
		def values = value as List
		List val2 = []
		values.each{ v ->
			Map val = [:]
			if((v.roles && checkDocAuthority(v.roles)) || !v.roles){
				val = [
					"paramType":"${v.paramType}",
					"name":"${v.name}",
					"description":"${v.description}"
				]
				
				if(v.paramType=='PKEY' || v.paramType=='FKEY'){
					val["idReferences"] = "${v.idReferences}"
				}
		
				if(v.required==false){
					val["required"] = false
				}
				if(v.mockData){
					val["mockData"] = "${v.mockData}"
				}
				if(v.values){
					val["values"] = processDocValues(v.values)
				}
				val2.add(val)
			}
		}
		return val2
	}
	
	private ArrayList processDocErrorCodes(HashSet error){
		def errors = error as List
		def err = []
		errors.each{ v ->
			def code = ['code':v.code,'description':"${v.description}"]
			err.add(code)
		}
		return err
	}
	
	private Map processJson(ArrayList returns){
		def json = [:]
		returns.each{ p ->
			def j = [:]
			if(p?.values){
				j["${p.name}"]=[]
			}else{
				j = (p?.mockData?.trim())?["${p.name}":"${p.mockData}"]:["${p.name}":"${grailsApplication.config.apitoolkit.defaultData.(p.paramType)}"]
			}
			j.each(){ key,val ->
				if(val instanceof List){
					def child = [:]
					val.each(){ it ->
						it.each(){ key2,val2 ->
							child["${key2}"] ="${val2}"
						}
					}
					json["${key}"] = child
				}else{
					json["${key}"]=val
				}
			}
		}
		if(json){
			json = json as JSON
			json = json.toString().replaceAll("\\{\n","\\{<br><div style='padding-left:2em;'>")
			json = json.toString().replaceAll("}"," </div>}<br>")
			json = json.toString().replaceAll(",",",<br>")
		}
		return json
	}
	
	Map generateApiDoc(String controllername, String actionname){
		Map doc = [:]
		def cont = apiCacheService.getApiCache(controllername)
		if(cont){
			def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', controllername)
			for (Method method : controller.getClazz().getDeclaredMethod(actionname)){
				if(method.isAnnotationPresent(Api)) {
					String path = "/${grailsApplication.config.apitoolkit.apiName}_${grailsApplication.metadata['app.version']}/JSON/${controllername}/${actionname}"
					doc[("${actionname}".toString())] = ["path":"${path}","method":cont[("${actionname}".toString())]["method"],"description":cont[("${actionname}".toString())]["description"]]
					
					if(cont["${actionname}"]["receives"]){
						doc[("${actionname}".toString())]["receives"] = processDocValues(cont[("${actionname}".toString())]["receives"] as HashSet)
					}
					
					if(cont["${actionname}"]["returns"]){
						doc[("${actionname}".toString())]["returns"] = processDocValues(cont[("${actionname}".toString())]["returns"] as HashSet)
						doc[("${actionname}".toString())]["json"] = processJson(doc[("${controllername}".toString())][("${actionname}".toString())]["returns"])
					}
					
					if(cont["${actionname}"]["errorcodes"]){
						doc[("${actionname}".toString())]["errorcodes"] = processDocErrorCodes(cont[("${actionname}".toString())]["errorcodes"] as HashSet)
					}
				}else{
					// ERROR: method at '${controllername}/${actionname}' does not have API annotation
				}
			}
		}
		return doc
	}
	
	Map isChainedApi(Map map,List path){
		def pathSize = path.size()
		//String uri = ''
		Map uri = [:]
		for (int i = 0; i < path.size(); i++) {
			if(path[i]){
				def val=path[i]
				def temp = val.split('=')
				String pathKey = temp[0]
				String pathVal = (temp.size()>1)?temp[1]:null

				if(pathKey=='null'){
					pathVal = pathVal.split('/').join('.')
						if(map["${pathVal}"]){
							if(map["${pathVal}"] in java.util.Collection){
								map = map["${pathVal}"]
							}else{
								if(map["${pathVal}"].toString().isInteger()){
									if(i==(pathSize-1)){
										def newMap = ["${pathVal}":map["${pathVal}"]]
										map = newMap
									}else{
										params.id = map["${pathVal}"]
									}
								}else{
									def newMap = ["${pathVal}":map["${pathVal}"]]
									map = newMap
								}
							}
						}else{
							return uri
						}
				}else{
					if(!uri['id']){
						uri['id'] = map["${pathVal}"]
					}
					uri['controller'] = pathKey.split('/')[0]
					uri['action'] = pathKey.split('/')[1]
					if(i+1<=path.size()-1){
						uri['params'] = [:]
						path[i+1..path.size()-1].each{
							 def tmp = it.split('=')
							 uri['params'][tmp[0]] = tmp[1]
						}
					}
					return uri
					break
				}
			}
		}
	}
	
	
	
	/*
	 * Returns chainType
	 * 1 = prechain
	 * 2 = postchain
	 * 3 = illegal combination
	 */
	int checkChainedMethodPosition(List uri,List path){
		boolean preMatch = false
		boolean postMatch = false
		boolean pathMatch = false
		Long pathSize = path.size()
		
		// prematch check
		def request = getRequest()
		String method = net.nosegrind.apitoolkit.Method["${request.method.toString()}"].toString()
		def cache = apiCacheService.getApiCache(uri[0])
		//def methods = cache["${uri[1]}"]['method'].replace('[','').replace(']','').split(',')*.trim() as List
		def methods = cache["${uri[1]}"]['method'][1..-2].split(',')*.trim() as List
		if(method=='GET'){
			if(!methods.contains(method)){ preMatch = true }
		}else{
			if(methods.contains(method)){ preMatch = true }
		}
		
		// postmatch check
		if(pathSize>1){
			def last=path.last()?.split('=')
			if(last[0] && last[0]!='null'){
				List last2 = last[0].split('/')
				cache = apiCacheService.getApiCache(last2[0])
				//methods = cache["${last2[1]}"]['method'].replace('[','').replace(']','').split(',')*.trim() as List
				methods = cache["${last2[1]}"]['method'][1..-2].split(',')*.trim() as List
				if(method=='GET'){
					if(!methods.contains(method)){ postMatch = true }
				}else{
					if(methods.contains(method)){ postMatch = true }
				}
			}
		}
		
		// path check
		int start = 1
		int end = pathSize-2
		if(start<end){
			path(start..end).each{ val ->
				if(val){
					List temp=val.split('=')
					List temp2 = temp[0].split('/')
					cache = apiCacheService.getApiCache(temp2[0])
					//methods = cache["${temp2[1]}"]['method'].replace('[','').replace(']','').split(',')*.trim() as List
					methods = cache["${temp2[1]}"]['method'][1..-2].split(',')*.trim() as List
					if(method=='GET'){
						if(!methods.contains(method)){ pathMatch = true }
					}else{
						if(methods.contains(method)){ pathMatch = true }
					}
				}
			}
		}
		
		if(pathMatch || (preMatch && postMatch)){
			return 3
		}else{
			if(preMatch){
				return 1
			}else if(postMatch){
				return 2
			}
		}
		
		// path but but no position
		// could be trying to make api call and url encode data
		// if so, not restful; does not comply
		return 3

	}
	
	Map convertModel(Map map){
		Map newMap = [:]
		String k = map.entrySet().toList().first().key
		
		if(map && (!map?.response && !map?.metaClass && !map?.params)){
			if(grailsApplication.isDomainClass(map[k].getClass())){
				newMap = formatDomainObject(map[k])
			}else{
				map[k].each{ key, val ->
					if(val){
						if(grailsApplication.isDomainClass(val.getClass())){
							newMap[key]=formatDomainObject(val)
						}else{
							if(val in java.util.ArrayList || val in java.util.List){
								newMap[key] = val
							}else if(val in java.util.Map){
								newMap[key]= val
							}else{
								newMap[key]=val.toString()
							}
						}
					}
				}
			}
		}
		return newMap
	}

	Map formatDomainObject(Object data){
		def nonPersistent = ["log", "class", "constraints", "properties", "errors", "mapping", "metaClass","maps"]
		def newMap = [:]
		
		if(data?.id){
			newMap['id'] = data.id
		}
		data.getProperties().each { key, val ->
			if (!nonPersistent.contains(key)) {
				if(grailsApplication.isDomainClass(val.getClass())){
					newMap.put key, val.id
				}else{
					newMap.put key, val
				}
			}
		}
		return newMap
	}
}
