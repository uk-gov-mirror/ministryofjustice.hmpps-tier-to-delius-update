package uk.gov.justice.digital.hmpps.hmppstiertodeliusupdate.integration.endtoend

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.google.gson.Gson
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest()
@ActiveProfiles("test")
abstract class MockedEndpointsTestBase {
  @Qualifier("awsSqsClient")
  @Autowired
  internal lateinit var awsSqsClient: AmazonSQS

  @Value("\${sqs.queue}")
  lateinit var queue: String

  var hmppsTier: ClientAndServer = ClientAndServer.startClientAndServer(8091)
  var communityApi: ClientAndServer = ClientAndServer.startClientAndServer(8092)

  private var oauthMock: ClientAndServer = ClientAndServer.startClientAndServer(9090)

  private val gson: Gson = Gson()

  @BeforeEach
  fun before() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queue))
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    setupOauth()
  }

  @AfterEach
  fun reset() {
    hmppsTier.reset()
    communityApi.reset()
    oauthMock.reset()
  }

  @AfterAll
  fun tearDownServer() {
    hmppsTier.stop()
    communityApi.stop()
    oauthMock.stop()
  }

  fun setupOauth() {
    val response = HttpResponse.response().withContentType(MediaType.APPLICATION_JSON)
      .withBody(gson.toJson(mapOf("access_token" to "ABCDE", "token_type" to "bearer")))
    oauthMock.`when`(HttpRequest.request().withPath("/auth/oauth/token").withBody("grant_type=client_credentials")).respond(response)
  }

  fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queue, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyNotVisibleOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queue, listOf("ApproximateNumberOfMessagesNotVisible"))
    return queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt()
  }
}
