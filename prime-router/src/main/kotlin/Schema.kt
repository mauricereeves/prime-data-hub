package gov.cdc.prime.router

/**
 * A PRIME schema contains a collection of data elements, mappings, validations and standard algorithms
 * needed to translate data to the form that a public health authority needs.
 *
 * Schemas can be either be `basedOn` another schema or `extend` another schema. If
 * a schema is `basedOn` another schema, the elements of the schema that match the
 * the elements of the base schema inherit their properties. The `basedOn` feature is
 * useful when defining a state CSV schema because it allows the state schema to only specify the names
 * of the elements it wants and the unique properties of that element. For example:
 *
 * <code>
 * - name: state_schema
 *   basedOn: covid-19 # means all elements inherit properties from this schema
 *   elements:
 *   - name: patient_first_name
 *     csvFields: [{name: PatientFirstName }] # adds a property that is not in the base schema
 * </code>
 *
 * Schemas can also have a `extends` relationship with another schema. Like a `basedOn` relationship,
 * the elements of a schema inherit from the `extends` schema. In addition, all the elements of the `extends`
 * schema are appended to the schema. The `extends` relationship is useful in building HL7 schemas, because
 * these schema mostly have a common element set.
 *
 * @param name is the unique name of the schema
 * @param topic defines the set of schemas that can translate with each other
 * @param elements are the data elements of the schema
 * @param trackingElement is the element that is unique per item and is used for tracking
 * @param description is the text description of the schema
 * @param referenceUrl is a link to a standard document used to create the schema
 * @param extends is a name of a schema that this schema extends.
 * @param basedOn is a name of a schema that this schema is based on
 */
data class Schema(
    val name: String,
    val topic: String,
    val elements: List<Element> = emptyList(),
    val trackingElement: String? = null, // the element to use for tracking this test
    val description: String? = null,
    val referenceUrl: String? = null,
    val extends: String? = null,
    val basedOn: String? = null,
) {
    val baseName: String get() = formBaseName(name)
    val csvFields: List<Element.CsvField> get() = elements.flatMap { it.csvFields ?: emptyList() }
    private val elementIndex: Map<String, Int> = elements.mapIndexed { index, element -> element.name to index }.toMap()

    // A mapping maps from one schema to another
    data class Mapping(
        val toSchema: Schema,
        val fromSchema: Schema,
        val useDirectly: Map<String, String>,
        val useValueSet: Map<String, String>,
        val useMapper: Map<String, Mapper>,
        val useDefault: Set<String>,
        val missing: Set<String>,
    )

    fun findElement(name: String): Element? {
        return elementIndex[name]?.let { elements[it] }
    }

    fun containsElement(name: String): Boolean {
        return elementIndex[name] != null
    }

    fun buildMapping(toSchema: Schema): Mapping {
        if (toSchema.topic != this.topic) error("Trying to match schema with different topics")

        val useDirectly = mutableMapOf<String, String>()
        val useValueSet: MutableMap<String, String> = mutableMapOf<String, String>()
        val useMapper = mutableMapOf<String, Mapper>()
        val useDefault = mutableSetOf<String>()
        val missing = mutableSetOf<String>()

        toSchema.elements.forEach { toElement ->
            findMatchingElement(toElement)?.let {
                useDirectly[toElement.name] = it
                return@forEach
            }
            toElement.mapper?.let {
                val name = Mappers.parseMapperField(it).first
                useMapper[toElement.name] = Metadata.findMapper(name) ?: error("Mapper $name is not found")
                return@forEach
            }
            if (toElement.required == true) {
                missing.add(toElement.name)
            } else {
                useDefault.add(toElement.name)
            }
        }
        return Mapping(toSchema, this, useDirectly, useValueSet, useMapper, useDefault, missing)
    }

    private fun findMatchingElement(matchElement: Element): String? {
        return findElement(matchElement.name)?.name
    }

    companion object {
        fun formBaseName(name: String): String {
            if (!name.contains("/")) return name
            return name.split("/").last()
        }
    }
}