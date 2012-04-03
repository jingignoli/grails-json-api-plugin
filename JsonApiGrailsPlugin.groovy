import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.plugins.json.api.Api
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import grails.converters.JSON
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import java.sql.Timestamp

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

    static EXCLUDES = "excludes"
    static INCLUDES = "includes"
    static API_CONFIG = "apiConfig"
    static ID = "id"
    static CLASS_NAME = "className"
    static DEFAULT_CONFIG = "default"

    def doWithApplicationContext = {
        JSON.registerObjectMarshaller(Enum) { it?.name() }
        JSON.registerObjectMarshaller(Date) { it?.format("yyyy-MM-dd'T'HH:mm:ssZ") }
        JSON.registerObjectMarshaller(Timestamp) { it?.format("yyyy-MM-dd'T'HH:mm:ssZ") }
    }

    def doWithDynamicMethods = { ctx ->

        def g = application.mainContext.getBean(ApplicationTagLib)

        application.domainClasses.each { grailsClass ->

            def clazz = grailsClass.clazz
            def mc = grailsClass.clazz.metaClass

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
            simplesFields << ID

            // Removes Excluded fields
            simplesFields = simplesFields - apiConfig[EXCLUDES]

            // Add Included fields
            def nestedFields = apiConfig[INCLUDES] ?: []

            // Clean apiConfig
            apiConfig.remove EXCLUDES
            apiConfig.remove INCLUDES

            // Add default empty config
            apiConfig[DEFAULT_CONFIG] = []

            // Prepare configs
            def configs = [:]

            apiConfig.each {k, publicFields ->

                def allFields = publicFields + nestedFields

                def simples = simplesFields
                def oneToOne = []
                def oneToMany = []

                configs[k] = [simples: simples, oneToOne: oneToOne, oneToMany: oneToMany]

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

                def rootDelegate = delegate

                def result = simples.collectEntries {[(it): rootDelegate.getProperty(it)]}

                // Add explicitly className
                result[CLASS_NAME] = grailsClass.name

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