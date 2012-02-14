package org.codehaus.groovy.grails.plugins.json.api


import grails.converters.JSON
import javax.servlet.http.HttpServletResponse
import org.apache.commons.logging.LogFactory

class Crud {

    private static final log = LogFactory.getLog(this)

    Class domainClass


    static BEFORE = "before"
    static AFTER = "after"
    static ID = "id"
    static PUBLIC_MAP = "publicMap"
    static PARAMS = "params"

    /**
     *

     * @return a map with the status:
     * - 400 if id not given,
     * - 404 if entity not found,
     * - 500 if error occured while deleting,
     * - 204 if success
     */

    def delete(args = [(ID): null, (BEFORE): null, (AFTER): null]) {

        if ( !args[ID] ) {
            return [status: HttpServletResponse.SC_BAD_REQUEST]
        }

        def domainObject = domainClass.get(args[ID])


        if ( !domainObject ) {
            return [status: HttpServletResponse.SC_NOT_FOUND]
        }

        try {

            args[BEFORE]?.call(domainObject)

            domainObject.delete()

            args[AFTER]?.call(domainObject)

            return [status: HttpServletResponse.SC_NO_CONTENT]

        } catch (Exception ex) {
            log.error "Error while deleting ${domainClass}=[${domainObject}] with id=[${args[ID]}]", ex
            return [status: HttpServletResponse.SC_INTERNAL_SERVER_ERROR]
        }
    }

    def save(args = [(ID): null, (PARAMS): null, (BEFORE): null, (AFTER): null, (PUBLIC_MAP): null]) {

        try {

            def domainObject = args[ID] ? domainClass.get(args[ID]) : domainClass.newInstance()

            if ( !domainObject ) {

                return [status: HttpServletResponse.SC_NOT_FOUND]

            } else {

                domainObject.bindData(args[(PARAMS)])

                // Check constraints before saving
                if ( domainObject.validate() ) {

                    args[BEFORE]?.call(domainObject)

                    domainObject.save(failOnError: true, flush: true)

                    args[AFTER]?.call(domainObject)

                    return [status: HttpServletResponse.SC_OK, text: domainObject.asPublicMap(args[PUBLIC_MAP]) as
                    JSON]

                } else {

                    log.error "${domainClass}=[${domainObject}] with id=[${args[ID]}] has validation errors"

                    domainObject.errors.allErrors.each {
                        log.error it
                    }

                    return [status: HttpServletResponse.SC_BAD_REQUEST, text: domainObject.asPublicMap(args[PUBLIC_MAP]) as
                    JSON]
                }
            }

        } catch (Exception ex) {
            log.error "Error while saving ${domainClass} with id=[${args[ID]}] and params=[${args[(PARAMS)]}]", ex
            [status: HttpServletResponse.SC_INTERNAL_SERVER_ERROR]
        }
    }

}