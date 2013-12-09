package net.nosegrind.restrpc;

import grails.util.Holders

@grails.validation.Validateable
class ParamsDescriptor {

	def grailsApplication
	
	String paramType
	String name
	String idReferences
	String description
	String[] roles = [];
	boolean required = true
	String mockData = Holders.config.restrpc."${paramType}"
	ParamsDescriptor[] values = []

	static constraints = { 
		paramType(nullable:false,maxSize:100,inList: ["PKEY", "FKEY", "INDEX","STRING","LONG","BOOLEAN","FLOAT","BIGDECIMAL","EMAIL","URL"])
		name(nullable:false,maxSize:100)
		idReferences(maxSize:100, validator: { val, obj ->
			if (paramType!="PKEY" && paramType!="FKEY") {
			  return ['nullable']
			}else {
			  return true
			}
		})
		description(nullable:false,maxSize:1000)
		roles(nullable:true)
		mockData(nullable:true)
		values(nullable:true)
	} 
}