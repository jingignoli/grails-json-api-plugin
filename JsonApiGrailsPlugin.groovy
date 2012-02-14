import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.plugins.json.api.Crud

class JsonApiGrailsPlugin {

    def version = "0.1"
    def grailsVersion = "2.0 > *"
    def dependsOn = [:]
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def title = "Json Api Plugin"
    def author = "Mathieu Perez, Julie Ingignoli"
    def authorEmail = "mathieu.perez@novacodex.net, julie.ingignoli@novacodex.net"
    def description = '''\
        Add Crud operations on Grails Domain Classes and exposition method  in JSON
        '''

    def documentation = "http://grails.org/plugin/json-api"

    def license = "APACHE"

    def organization = [name: 'NovaCodex', url: 'http://www.novacodex.net/']

    def developers = [
            [name: 'Mathieu Perez', email: 'mathieu.perez@novacodex.net'],
            [name: 'Julie Ingignoli', email: 'julie.ingignoli@novacodex.net']]


    def doWithDynamicMethods = { ctx ->


        def publicMapDefaultKey = "__"

        application.domainClasses.each { grailsClass ->

            def clazz = grailsClass.clazz
            def mc = grailsClass.clazz.metaClass

            // -----------------------------------------
            // .asPublicMap()
            // .asPublicMap(String key)
            // -----------------------------------------
            def searchBarToken = ClassPropertyFetcher.forClass(clazz).getStaticPropertyValue("searchBarToken", Closure)

            def publicFieldsAsMap = ClassPropertyFetcher.forClass(clazz).getStaticPropertyValue("publicFields", Map) ?: [:]

            def simplesFields = grailsClass.persistentProperties.findAll { property ->
                !(property.isManyToOne() || property.isOneToOne() || property.isOneToMany() || property.isManyToMany())
            }.collect {it.name} + ["id"]


            //Excluded fields
            simplesFields = simplesFields - publicFieldsAsMap["excludedFields"]

            publicFieldsAsMap.remove("excludedFields")

            //Included fields
            def includedFields = publicFieldsAsMap["includedFields"] ?: []

            publicFieldsAsMap[publicMapDefaultKey] = []

            def tt = [:]

            publicFieldsAsMap.each {k, publicFields ->
                def allFields = publicFields + includedFields

                def simples = simplesFields
                def oneToOne = []
                def oneToMany = []

                tt[k] = [simples: simples, oneToOne: oneToOne, oneToMany: oneToMany]

                allFields.each { publicField ->
                    def property = grailsClass.getPropertyByName(publicField)

                    if ( property.isManyToOne() || property.isOneToOne() || property.isEmbedded() ) {
                        oneToOne << publicField
                    } else if ( property.isOneToMany() || property.isManyToMany() ) {
                        oneToMany << publicField
                    } else {
                        simples << publicField
                    }
                }
            }

            mc.asPublicMap = { String key = publicMapDefaultKey ->

                def publicFields = tt[key] ?: tt[publicMapDefaultKey]

                def simples = publicFields.simples ?: []
                def oneToOne = publicFields.oneToOne ?: []
                def oneToMany = publicFields.oneToMany ?: []

                def rootDelegate = delegate

                def result = simples.collectEntries {[(it): rootDelegate.getProperty(it)]}

                result["className"] = grailsClass.name

                if ( searchBarToken ) {
                    result["searchBarToken"] = searchBarToken.call(rootDelegate)
                }

                oneToOne.each { publicField ->
                    result[publicField] = rootDelegate.getProperty(publicField)?.asPublicMap(key)
                }

                oneToMany.each { publicField ->
                    result[publicField] = rootDelegate.getProperty(publicField)?.collect { it.asPublicMap(key) }
                }

                if ( rootDelegate.hasErrors() ) {

                    result.errors = [:]

                    rootDelegate.errors.allErrors.each { error ->
                        result.errors[error.field] = g.message(error: error)
                    }
                }

                result
            }


            // -----------------------------------------
            // .crud()
            // -----------------------------------------

            def crud = new Crud(domainClass: clazz)

            grailsClass.metaClass.static.crud = {
                crud
            }

            // -----------------------------------------
            // .saveWithParams()
            // -----------------------------------------
            grailsClass.metaClass.static.saveWithParams = { id, params ->

                def command

                if ( id ) {

                    command = clazz.get(id)

                    if ( !command ) {
                        return null
                    }

                } else {
                    command = clazz.newInstance()
                }

                command.bindData(params)

                // Check constraints before saving
                if ( command.validate() ) {
                    command.save(flush: true)
                }

                command
            }

        }

    }

}
