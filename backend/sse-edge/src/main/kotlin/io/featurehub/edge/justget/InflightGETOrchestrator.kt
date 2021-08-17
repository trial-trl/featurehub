package io.featurehub.edge.justget

import io.featurehub.dacha.api.DachaClientServiceRegistry
import io.featurehub.edge.FeatureTransformer
import io.featurehub.edge.KeyParts
import io.featurehub.edge.strategies.ClientContext
import io.featurehub.sse.model.Environment
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import jakarta.inject.Inject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

interface EdgeConcurrentRequestPool {
  fun execute(task: Runnable)
}

interface InflightGETSubmitter {
  /*
   * we have a result, don't use this request again
   */
  fun removeGET(key: KeyParts)

  /*
   * Requests a bunch of environment details from Dacha in the most efficient way possible
   */
  fun request(keys: List<KeyParts>, context: ClientContext): List<Environment>
}

class InflightGETOrchestrator @Inject constructor(
  private val featureTransformer: FeatureTransformer, private val dachaApi: DachaClientServiceRegistry,
  private val executor: EdgeConcurrentRequestPool
) : InflightGETSubmitter {
  private val getMap = ConcurrentHashMap<KeyParts, InflightGETRequest>()

  companion object {
    val inflightGauge = Gauge.build("edge_get_inflight_requests", "Inflight GET request Counter").register()
  }

  override fun request(keys: List<KeyParts>, context: ClientContext): List<Environment> {
    inflightGauge.inc()

    val future = CompletableFuture<List<Environment>>()

    // get an existing or create a new one for each of the sdk urls
    val getters = keys
      .filter { key -> dachaApi.getApiKeyService(key.cacheName) != null } // only caches that exist
      .map { key ->
      getMap.computeIfAbsent(key) { InflightGETRequest(dachaApi.getApiKeyService(key.cacheName), key, executor, this) }
    }.toList()

    // now create a collector for the requests to notify
    val action = InflightGETCollection(getters, featureTransformer, context, future)

    // and tell them to go get the data or add us to their list
    getters.forEach { getter -> getter.add(action) }

    val result = future.get()

    inflightGauge.dec()

    return result
  }

  override fun removeGET(key: KeyParts) {
    println("number of listeners: ${getMap[key]?.notifyListener?.size ?: "none"}")
    getMap.remove(key)
  }
}
