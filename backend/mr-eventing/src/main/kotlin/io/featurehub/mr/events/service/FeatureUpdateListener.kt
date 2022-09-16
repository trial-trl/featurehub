package io.featurehub.mr.events.service

import io.featurehub.db.api.RolloutStrategyValidator.InvalidStrategyCombination
import io.featurehub.db.listener.FeatureUpdateBySDKApi
import io.featurehub.mr.events.common.listeners.EdgeUpdateListener
import io.featurehub.mr.messaging.StreamedFeatureUpdate
import io.featurehub.mr.model.FeatureValue
import io.featurehub.mr.model.FeatureValueType
import org.slf4j.LoggerFactory

open class FeatureUpdateListener(
  private val featureUpdateBySDKApi: FeatureUpdateBySDKApi
) : EdgeUpdateListener {

  override fun processUpdate(update: StreamedFeatureUpdate) {
    try {
      log.debug("received update {}", update)
      featureUpdateBySDKApi.updateFeature(
        update.apiKey, update.environmentId, update.featureKey, update.updatingValue
      ) { valueType: FeatureValueType? ->
        val fv = FeatureValue()
          .key(update.featureKey)
          .locked(update.lock != null && update.lock!!)
        when (valueType) {
          FeatureValueType.BOOLEAN -> fv.valueBoolean(update.valueBoolean)
          FeatureValueType.STRING -> fv.valueString(update.valueString)
          FeatureValueType.NUMBER -> fv.valueNumber = update.valueNumber
          FeatureValueType.JSON -> fv.valueJson(update.valueString)
          else -> {
          }
        }
        fv
      }
    } catch (ignoreEx: InvalidStrategyCombination) {
      // ignore
    } catch (failed: Exception) {
      log.error("Failed to update {}", update, failed)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(FeatureUpdateListener::class.java)
  }
}
