package gov.cdc.prime.router

/**
 * A `OrganizationClient` represents the agent that is sending reports to
 * to the data hub (minus the credentials used by that agent, of course). It
 * contains information about the specific topic and schema that the client uses.
 */
data class OrganizationClient(
    val name: String,
    val format: Format,
    val topic: String,
    val schema: String,
) {
    lateinit var organization: Organization
    val fullName: String get() = "${organization.name}.${name}"

    enum class Format { CSV }
}