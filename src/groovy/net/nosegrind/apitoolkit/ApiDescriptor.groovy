package net.nosegrind.apitoolkit;

import net.nosegrind.apitoolkit.ErrorCodeDescriptor;
import net.nosegrind.apitoolkit.ParamsDescriptor;
import grails.validation.Validateable

//@grails.validation.Validateable
@Validateable
class ApiDescriptor {

	String method
	List apiRoles
	List hookRoles
	String name
    String description
	Map doc
    ParamsDescriptor[] receives
    ParamsDescriptor[] returns
    ErrorCodeDescriptor[] errorcodes

	static constraints = { 
		method(nullable:true,inList: ["GET","POST","PUT","DELETE"])
		apiRoles(nullable:true)
		name(nullable:true,maxSize:500)
		description(nullable:true,maxSize:1000)
		doc(nullable:true)
		receives(nullable:true)
		returns(nullable:true)
		errorcodes(nullable:true)
	} 
}