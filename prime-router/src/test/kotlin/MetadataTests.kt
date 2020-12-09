package gov.cdc.prime.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MetadataTests {
    @Test
    fun `test loading schema catalog`() {
        Metadata.loadSchemaCatalog("./src/test/unit_test_files")
        assertNotNull(Metadata.findSchema("lab_test_results_schema"))
        val elements = Metadata.findSchema("lab_test_results_schema")?.elements
        assertEquals("lab", elements?.get(0)?.name)
        assertEquals("extra", elements?.get(6)?.name)
        assertEquals(Element.Type.POSTAL_CODE, elements?.get(8)?.type)
    }

    @Test
    fun `test loading two schemas`() {
        val one = Schema(name = "one", topic = "test", elements = listOf(Element("a")))
        val two = Schema(name = "two", topic = "test", elements = listOf(Element("a"), Element("b")))
        Metadata.loadSchemas(listOf(one, two))
        assertNotNull(Metadata.findSchema("one"))
    }

    @Test
    fun `load valueSets`() {
        val one = ValueSet("one", ValueSet.SetSystem.HL7)
        val two = ValueSet("two", ValueSet.SetSystem.LOCAL)
        Metadata.loadValueSets(listOf(one, two))
        assertNotNull(Metadata.findValueSet("one"))
    }

    @Test
    fun `load value set directory`() {
        Metadata.loadValueSetCatalog("./src/test/unit_test_files")
        assertNotNull(Metadata.findValueSet("hl70136"))
    }

    @Test
    fun `test find schemas`() {
        val one = Schema(name = "One", topic = "test", elements = listOf(Element("a")))
        val two = Schema(name = "Two", topic = "test", elements = listOf(Element("a"), Element("b")))
        Metadata.loadSchemas(listOf(one, two))
        assertNotNull(Metadata.findSchema("one"))
    }

    @Test
    fun `test find client`() {
        Metadata.loadOrganizationList("./src/test/unit_test_files/organizations.yml")
        val client = Metadata.findClient("simple_report")
        assertNotNull(client)
    }

    @Test
    fun `test expanding schema`() {
        val elementName = "standard.patient_last_name"
        val standardSchemaName = "standard"
        val childSchemaName = "pdi-covid-19"

        // load our metadata
        Metadata.loadSchemaCatalog("./src/test/unit_test_files")
        // grab our two schemas, the child pdi one and the parent standard schema
        val childSchema = Metadata.findSchema(childSchemaName)
        val standardSchema = Metadata.findSchema(standardSchemaName)
        // verify they're both valid
        assertNotNull(childSchema)
        assertNotNull(standardSchema)
        // does the parent schema have the element we want?
        val elementNameParts = elementName.split(".")
        val baseElementName = elementNameParts[1]
        val baseElement = standardSchema.findElement(baseElementName)
        assertNotNull(baseElement, "base element for $baseElementName does not exist in standard schema")
        // does the child schema have an element that refers back?
        val standardLastNameElement = childSchema.findElement(elementName)
        assertNotNull(standardLastNameElement, "element doesn't exist in child schema with name $elementName")
        // make sure the elements were merged in
        val lastNameElement = childSchema.findElement("standard.patient_last_name")
        assertNotNull(lastNameElement, "last name element was null")
        assertNotNull(lastNameElement.documentation, "documentation string was null")
    }
}