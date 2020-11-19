package gov.cdc.prime.router.azure

import com.google.common.net.HttpHeaders
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import com.microsoft.azure.functions.annotation.StorageAccount
import gov.cdc.prime.router.ClientSource
import gov.cdc.prime.router.CsvConverter
import gov.cdc.prime.router.Metadata
import gov.cdc.prime.router.Report
import java.io.ByteArrayInputStream
import java.util.logging.Level

/**
 * Azure Functions with HTTP Trigger.
 */
class IngestFunction {
    private val clientName = "client"
    private val csvMimeType = "text/csv"

    /**
     * This function listens at endpoint "/api/report".
     * Ugly invocation that actually worked for me, run in prime-router/:
     *    curl -X POST -H "Content-Type: text/csv" --data-binary "@./src/test/unit_test_files/lab1-test_results-17-42-31.csv" "http://localhost:7071/api/report?topic=covid-19&filename=lab1-test_results-17-42-31.csv&schema-name=pdi-covid-19.schema"
     * Which returns the following upon success:
     *    {"filename":"lab1-test_results-17-42-31.csv","topic":"covid-19","schemaName":"pdi-covid-19.schema","action":"","blobURL":"http://azurite:10000/devstoreaccount1/ingested/lab1-test_results-17-42-31-pdi-covid-19.schema-3ddef736-55e1-4a45-ac41-f74086aaa654.csv"}
     */
    @FunctionName("reports")
    @StorageAccount("AzureWebJobsStorage")
    fun run(
        @HttpTrigger(
            name = "req",
            methods = [HttpMethod.POST],
            authLevel = AuthorizationLevel.FUNCTION
        ) request: HttpRequestMessage<String?>,
        context: ExecutionContext,
    ): HttpResponseMessage {
        // First load metadata
        try {
            val baseDir = System.getenv("AzureWebJobsScriptRoot")
            Metadata.loadAll("$baseDir/metadata")
        } catch (e: Exception) {
            context.logger.log(Level.SEVERE, e.message, e)
            request
                .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to load metadata")
                .build()
        }

        // Validate the incoming CSV by converting it to a report.
        val report = try {
            createReportFromRequest(request, context)
        } catch (e: Exception) {
            context.logger.log(Level.INFO, "Bad request from e.message", e)
            return request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(e.message)
                .build()
        }
        context.logger.info("Successfully read ${report.id}. Preparing to queue it for processing.")

        // Queue the report for further processing.
        return try {
            ReportQueue.sendReport(ReportQueue.Name.INGESTED, report)
            request
                .createResponseBuilder(HttpStatus.CREATED)
                .body(createResponseBody(report))
                .build()
        } catch (e: Exception) {
            context.logger.log(Level.SEVERE, e.message, e)
            request
                .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to send to queue for further work.")
                .build()
        }
    }

    fun createReportFromRequest(request: HttpRequestMessage<String?>, context: ExecutionContext): Report {
        // TODO: check for client-id in Auth token when implement auth
        val clientName = request.headers.getValue(clientName)
        val client = Metadata.findClient(clientName) ?: error("$clientName does not match known clients")
        val contentType = request.headers.getValue(HttpHeaders.CONTENT_TYPE.toLowerCase())
        val schema = Metadata.findSchema(client.schema) ?: error("missing schema for $clientName")
        val content = request.body?.toByteArray() ?: error("missing body content")
        return when (contentType) {
            csvMimeType -> {
                CsvConverter.read(
                    schema,
                    ByteArrayInputStream(content),
                    ClientSource(organization = client.organization.name, client = client.name)
                )
            }
            else -> error("$contentType is not supported, expected text/csv")
        }
    }

    fun createResponseBody(report: Report): String {
        return """
        {
          "id": "${report.id}"
        }
        """.trimIndent()
    }


}