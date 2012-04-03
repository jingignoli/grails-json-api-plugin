package org.codehaus.groovy.grails.plugins.json.api

import javax.servlet.http.HttpServletResponse
import org.apache.commons.logging.LogFactory

class Api {

    private static final log = LogFactory.getLog(this)

    Class domainClass

    static BEFORE = "before"
    static AFTER = "after"
    static ID = "id"
    static CONFIG = "config"
    static PARAMS = "params"
    static MAX = "max"
    static OFFSET = "offset"

    def get(args = [(ID): null, (CONFIG): null]) {

        try {

            def domainObject = args[ID] ? domainClass.get(args[ID]) : domainClass.newInstance()

            if ( !domainObject ) {

                return [status: HttpServletResponse.SC_NOT_FOUND]

            } else {

                return [status: HttpServletResponse.SC_OK, text: domainObject.asJSON(args[CONFIG])]
            }

        } catch (Exception ex) {
            log.error "Error while getting ${domainClass} with id=[${args[ID]}]", ex
            [status: HttpServletResponse.SC_INTERNAL_SERVER_ERROR]
        }

    }

    def list(args = [(MAX): 10, (OFFSET): 0, (CONFIG): null]) {
        domainClass.findAll(args.subMap([MAX, OFFSET]))*.asJSON(args[CONFIG])
    }

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

    def save(args = [(ID): null, (PARAMS): null, (BEFORE): null, (AFTER): null, (CONFIG): null]) {

        try {

            def domainObject = args[ID] ? domainClass.get(args[ID]) : domainClass.newInstance()

            if ( !domainObject ) {

                return [status: HttpServletResponse.SC_NOT_FOUND]

            } else {

                domainObject.properties = args[(PARAMS)]

                // Check constraints before saving
                if ( domainObject.validate() ) {

                    args[BEFORE]?.call(domainObject)

                    domainObject.save(failOnError: true, flush: true)

                    args[AFTER]?.call(domainObject)

                    return [status: HttpServletResponse.SC_OK, text: domainObject.asJSON(args[CONFIG])]

                } else {

                    log.error "${domainClass}=[${domainObject}] with id=[${args[ID]}] has validation errors"

                    domainObject.errors.allErrors.each {
                        log.error it
                    }

                    return [status: HttpServletResponse.SC_BAD_REQUEST, text: domainObject.asJSON(args[CONFIG])]
                }
            }

        } catch (Exception ex) {
            log.error "Error while saving ${domainClass} with id=[${args[ID]}] and params=[${args[(PARAMS)]}]", ex
            [status: HttpServletResponse.SC_INTERNAL_SERVER_ERROR]
        }
    }

}