import java.util.Map

import grails.converters.JSON
import grails.converters.XML
import net.nosegrind.restrpc.Api
import net.nosegrind.restrpc.RestMethod

class RestRPCFilters {
	
	def restRPCService
	
	def filters = {
		restrpc(controller:'*', action:'*'){
			before = {
				/*
				 * DO ANNO CHECKS BEFORE METHOD CALL SO EDIT/DELETE DOESN'T GET CALLED AND
				 * END USER DOESN'T HAVE TO DO CHECK; CHECK IS OPTIONAL. WE ALWAYS DO CHECK
				 * TO PROTECT DATA AND THEY CAN DO OPTIONAL CHECK TO SEND DIFFERENT DATA
				 */

				def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', params.controller)
				params.action = (params.action)?params.action:'index'
				def action = controller?.getClazz()?.getDeclaredMethod(params.action)
				// IF THERE IS AN ACTION, WE PROCESS ELSE WE IGNORE; COULD BE INDEX
				// WHICH WILL REDIRECT
				if (action) {
					if (action.isAnnotationPresent(Api)) {
						if (restRPCService.isApiCall()) {
							// CHECK ANNO TO SEE IF ITS POST/PUT/DELETE
							// IF SO, CHECK METHOD MATCH
							def anno = action.getAnnotation(Api)
							if(!restRPCService.isRequestMatch(anno.method().toString())){
								return false
							}
						}else{
							return false
						}
					}
				}
			}
			
			after = { Map model ->

				def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', params.controller)
				def action = controller?.getClazz()?.getDeclaredMethod(params.action)
				if (action.isAnnotationPresent(Api)) {
					if (restRPCService.isApiCall()) {
						def anno = action.getAnnotation(Api)
						if(!anno){
							render(controller:params.controller,action:params.action, model:model)
						}
						
						def newModel = (grailsApplication.isDomainClass(model.getClass()))?model:restRPCService.formatModel(model)
						
						String format = params.format
		
						def queryString = request.'javax.servlet.forward.query_string'
						def path = (queryString)?queryString.split('&'):[]
		
						def lastKey
						switch(anno.method().toString()) {
							case 'GET':
								if(!newModel.isEmpty()){
									switch(params.format){
										case 'JSON':
											def map = newModel
											def key
		
											if(path){
												def pathSize = path.size()
												path.eachWithIndex(){ val,i ->
													if(val){
														def temp = val.split('=')
														String pathKey = temp[0]
														String pathVal = (temp.size()>1)?temp[1]:null
		
														if(pathKey=='null'){
															pathVal = pathVal.split('/').join('.')
															if(map."${pathVal}"){
																if(map."${pathVal}" in java.util.Collection){
																	map = map."${pathVal}"
																}else{
																	if(map."${pathVal}".toString().isInteger()){
																		if(i==(pathSize-1)){
																			def newMap = ["${pathVal}":map."${pathVal}"]
																			map = newMap
																		}else{
																			params.id = map."${pathVal}"
																		}
																	}else{
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}
																}
															}else{
																String msg = "Path '${pathKey}' was unable to be parsed"
																return restRPCService._404_NOTFOUND(msg)
															}
														}else{
															def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
															uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
															redirect(uri: "${uri}")
														}
													}
												}
											}
											
											render(text:map as JSON, contentType: "application/json")
											//return false
											break
										case 'XML':
											def map = newModel
											def key
		
											if(path){
												def pathSize = path.size()
												path.eachWithIndex(){ val,i ->
													if(val){
														def temp = val.split('=')
														String pathKey = temp[0]
														String pathVal = (temp.size()>1)?temp[1]:null
		
														if(pathKey=='null'){
															pathVal = pathVal.split('/').join('.')
															if(map."${pathVal}"){
																if(map."${pathVal}" in java.util.Collection){
																	map = map."${pathVal}"
																}else{
																	if(map."${pathVal}".toString().isInteger()){
																		if(i==(pathSize-1)){
																			def newMap = ["${pathVal}":map."${pathVal}"]
																			map = newMap
																		}else{
																			params.id = map."${pathVal}"
																		}
																	}else{
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}
																}
															}else{
																String msg = "Path '${pathKey}' was unable to be parsed"
																return restRPCService._404_NOTFOUND(msg)
															}
														}else{
															def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
															uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
															redirect(uri: "${uri}")
														}
													}
												}
											}
		
											render(text:map as XML, contentType: "application/xml")
											//return false
											break
										case 'HTML':
											def map = newModel
											def key
		
											if(path){
												def pathSize = path.size()
												path.eachWithIndex(){ val,i ->
													if(val){
														def temp = val.split('=')
														String pathKey = temp[0]
														String pathVal = (temp.size()>1)?temp[1]:null
		
														if(pathKey=='null'){
															pathVal = pathVal.split('/').join('.')
															if(map."${pathVal}"){
																if(map."${pathVal}" in java.util.Collection){
																	map = map."${pathVal}"
																}else{
																	if(map."${pathVal}".toString().isInteger()){
																		if(i==(pathSize-1)){
																			def newMap = ["${pathVal}":map."${pathVal}"]
																			map = newMap
																		}else{
																			params.id = map."${pathVal}"
																		}
																	}else{
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}
																}
															}else{
																String msg = "Path '${pathKey}' was unable to be parsed"
																return restRPCService._404_NOTFOUND(msg)
															}
														}else{
															def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
															uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
															redirect(uri: "${uri}")
														}
													}
												}
											}
											
											def linkRels = []
											map.each(){ k,v ->
												def api = action.getAnnotation(Api)
												def returns = api.returns()
												returns.each{ p ->
													String paramType = p.paramType().toString()
													String name = p.name().toString()
													String belongsTo = p.belongsTo().toString()
													Integer paramKey = restRPCService.getKey(paramType)
													if(paramKey>0 && name==k){
														def temp = []
														if(paramKey==1){
															temp = restRPCService.createLinkRelationships(paramType,name,params.controller)
														}else{
															temp = restRPCService.createLinkRelationships(paramType,name,belongsTo)
															def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}/${belongsTo[0].toLowerCase()+belongsTo.substring(1)}/show/${v}"
															map[k] = "<a href=${uri}>${v}<a>"
														}
														linkRels.add(temp)
													}
												}
											}
											map['linkRelationships']=linkRels
											
											def json = map as JSON
											json = json.toString().replaceAll("\\{\n","\\{<br><div style='padding-left:2em;'>")
											json = json.toString().replaceAll("}"," </div>}<br>")
											json = json.toString().replaceAll(",",",<br>")
											
											render(text:json)
											//return false
											break
									}
								}
								break
							case 'POST':
								switch(params.format){
									case 'JSON':
										def map = newModel
										def key
		
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
										
										render(text:map as JSON, contentType: "application/json")
										break
									case 'XML':
										def map = newModel
										def key
		
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
										
										render(text:map as XML, contentType: "application/xml")
										return false
										break
								}
								break
							case 'PUT':
								switch(params.format){
									case 'JSON':
										def map = newModel
										def key
		
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
										
										render(text:map as JSON, contentType: "application/json")
										return false
										break
									case 'XML':
										def map = newModel
										def key
		
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
										
										render(text:map as XML, contentType: "application/xml")
										return false
										break
								}
								break
							case 'DELETE':
								switch(params.format){
									case 'JSON':
										def key
										
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
										return response.status
										break;
									case 'XML':
										def key
										
										if(path){
											def pathSize = path.size()
											path.eachWithIndex(){ val,i ->
												if(val){
													def temp = val.split('=')
													String pathKey = temp[0]
													String pathVal = (temp.size()>1)?temp[1]:null
		
													if(pathKey=='null'){
														pathVal = pathVal.split('/').join('.')
														if(map."${pathVal}"){
															if(map."${pathVal}" in java.util.Collection){
																map = map."${pathVal}"
															}else{
																if(map."${pathVal}".toString().isInteger()){
																	if(i==(pathSize-1)){
																		def newMap = ["${pathVal}":map."${pathVal}"]
																		map = newMap
																	}else{
																		params.id = map."${pathVal}"
																	}
																}else{
																	def newMap = ["${pathVal}":map."${pathVal}"]
																	map = newMap
																}
															}
														}else{
															String msg = "Path '${pathKey}' was unable to be parsed"
															return restRPCService._404_NOTFOUND(msg)
														}
													}else{
														def uri = "/${grailsApplication.config.restrpc.apiName}/${grailsApplication.metadata['app.version']}/${format}"
														uri += (params.id)?"${pathKey}/${params.id}":"${pathKey}"
														redirect(uri: "${uri}")
													}
												}
											}
										}
									
										return response.status
										break
								}
								break
						}
						
						return false
						
					}else{
						render (view:params.action,model:model)
					}
				}else{
					render (view:params.action,model:model)
				}
			}
			
		}

	}

}
			
