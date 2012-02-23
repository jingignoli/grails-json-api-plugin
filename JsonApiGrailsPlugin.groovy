import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.plugins.json.api.Api
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import grails.converters.JSON
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

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
        Add Api operations on Grails Domain Classes and exposition method  in JSON
        '''

    def documentation = "http://grails.org/plugin/json-api"

    def license = "APACHE"

    def organization = [name: 'NovaCodex', url: 'http://www.novacodex.net/']

    def developers = [
            [name: 'Mathieu Perez', email: 'mathieu.perez@novacodex.net'],
            [name: 'Julie Ingignoli', email: 'julie.ingignoli@novacodex.net']]

    // =============================================================================================

    static EXCLUDED_FIELDS = "excludedFields"
    static NESTED_FIELDS = "nestedFields"
    static API_CONFIG = "apiConfig"
    static IDENTITY = "id"
    static CLASS_NAME = "className"
    static CUSTOM_FIELDS = "customFields"
    static DEFAULT_CONFIG = "default"

    def watchedResources = "file:./grails-app/domain/**/*.groovy"

    def onChange = { event ->

    }

    def doWithDynamicMethods = { ctx ->

        def g = application.mainContext.getBean(ApplicationTagLib)

        application.domainClasses.each { grailsClass ->

            def clazz = grailsClass.clazz
            def mc = grailsClass.clazz.metaClass

            // -----------------------------------------
            // .bindData()
            // -----------------------------------------
            def bind = new BindDynamicMethod()

            mc.bindData = { Object args ->
                bind.invoke(delegate, "bindData", [delegate, args] as Object[])
            }

            mc.bindData = {  Object args, List disallowed ->
                bind.invoke(delegate, "bindData", [delegate, args, [exclude: disallowed]] as Object[])
            }

            mc.bindData = { Object args, List disallowed, String filter ->
                bind.invoke(delegate, "bindData", [delegate, args, [exclude: disallowed], filter] as Object[])
            }

            mc.bindData = { Object args, Map includeExclude ->
                bind.invoke(delegate, "bindData", [delegate, args, includeExclude] as Object[])
            }

            mc.bindData = { Object args, Map includeExclude, String filter ->
                bind.invoke(delegate, "bindData", [delegate, args, includeExclude, filter] as Object[])
            }

            mc.bindData = { Object args, String filter ->
                bind.invoke(delegate, "bindData", [delegate, args, filter] as Object[])
            }

            // -----------------------------------------
            // .asMap()
            // .asMap(String key)
            //
            // .asJSON()
            // .asJSON(String key)
            // -----------------------------------------
            def apiConfig = ClassPropertyFetcher.forClass(clazz).getStaticPropertyValue(API_CONFIG, Map) ?: [:]

            def simplesFields = grailsClass.persistentProperties.findAll { property ->
                !(property.isManyToOne() || property.isOneToOne() || property.isOneToMany() || property.isManyToMany())
            }.collect {it.name}

            // Add explicitly 'id' field
            simplesFields << IDENTITY

            // Removes Excluded fields
            simplesFields = simplesFields - apiConfig[EXCLUDED_FIELDS]

            // Get nested fields
            def nestedFields = apiConfig[NESTED_FIELDS] ?: []

            // Get customs fields
            def customsFields = apiConfig[CUSTOM_FIELDS] ?: [:]

            // Clean apiConfig
            apiConfig.remove EXCLUDED_FIELDS
            apiConfig.remove NESTED_FIELDS
            apiConfig.remove CUSTOM_FIELDS

            // Add default empty config
            apiConfig[DEFAULT_CONFIG] = []

            // Prepare configs
            def configs = [:]

            apiConfig.each {k, publicFields ->

                def allFields = publicFields + nestedFields

                def simples = simplesFields
                def oneToOne = []
                def oneToMany = []

                configs[k] = [
                        simples: simples,
                        oneToOne: oneToOne,
                        oneToMany: oneToMany,
                        customs: customsFields
                ]

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

            mc.asMap = { String key = DEFAULT_CONFIG ->

                def publicFields = configs[key] ?: configs[DEFAULT_CONFIG]

                def simples = publicFields.simples ?: []
                def oneToOne = publicFields.oneToOne ?: []
                def oneToMany = publicFields.oneToMany ?: []
                def customs = publicFields.customs ?: [:]

                def rootDelegate = delegate

                def result = simples.collectEntries {[(it): rootDelegate.getProperty(it)]}

                // Add explicitly className
                result[CLASS_NAME] = grailsClass.name

                // Add customs fields which are closures
                customs.each { k, closure ->
                    result[k] = closure.call(rootDelegate)
                }

                oneToOne.each { publicField ->
                    result[publicField] = rootDelegate.getProperty(publicField)?.asMap(key)
                }

                oneToMany.each { publicField ->
                    result[publicField] = rootDelegate.getProperty(publicField)?.collect { it.asMap(key) }
                }

                if ( rootDelegate.hasErrors() ) {

                    result.errors = [:]

                    rootDelegate.errors.allErrors.each { error ->
                        result.errors[error.field] = g.message(error: error)
                    }
                }

                result
            }

            mc.asJSON = {String key = DEFAULT_CONFIG ->
                delegate.asMap(key) as JSON
            }

            // -----------------------------------------
            // .api()
            // -----------------------------------------
            mc.static.api = {
                new Api(domainClass: clazz)
            }
        }
    }
}