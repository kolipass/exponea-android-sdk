package com.exponea.sdk

import android.content.Context
import com.exponea.sdk.manager.BackgroundTimerManager
import com.exponea.sdk.manager.BackgroundTimerManagerImpl
import com.exponea.sdk.manager.ConnectionManager
import com.exponea.sdk.manager.ConnectionManagerImpl
import com.exponea.sdk.manager.DisabledInAppMessageManagerImpl
import com.exponea.sdk.manager.EventManager
import com.exponea.sdk.manager.EventManagerImpl
import com.exponea.sdk.manager.FcmManager
import com.exponea.sdk.manager.FcmManagerImpl
import com.exponea.sdk.manager.FetchManager
import com.exponea.sdk.manager.FetchManagerImpl
import com.exponea.sdk.manager.FileManager
import com.exponea.sdk.manager.FileManagerImpl
import com.exponea.sdk.manager.FlushManager
import com.exponea.sdk.manager.FlushManagerImpl
import com.exponea.sdk.manager.IapManager
import com.exponea.sdk.manager.IapManagerImpl
import com.exponea.sdk.manager.InAppMessageManager
import com.exponea.sdk.manager.InAppMessageManagerImpl
import com.exponea.sdk.manager.PersonalizationManager
import com.exponea.sdk.manager.PersonalizationManagerImpl
import com.exponea.sdk.manager.ServiceManager
import com.exponea.sdk.manager.ServiceManagerImpl
import com.exponea.sdk.manager.SessionManager
import com.exponea.sdk.manager.SessionManagerImpl
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.network.ExponeaService
import com.exponea.sdk.network.ExponeaServiceImpl
import com.exponea.sdk.network.NetworkHandler
import com.exponea.sdk.network.NetworkHandlerImpl
import com.exponea.sdk.preferences.ExponeaPreferences
import com.exponea.sdk.preferences.ExponeaPreferencesImpl
import com.exponea.sdk.repository.CampaignRepository
import com.exponea.sdk.repository.CampaignRepositoryImpl
import com.exponea.sdk.repository.CustomerIdsRepository
import com.exponea.sdk.repository.CustomerIdsRepositoryImpl
import com.exponea.sdk.repository.DeviceInitiatedRepository
import com.exponea.sdk.repository.DeviceInitiatedRepositoryImpl
import com.exponea.sdk.repository.EventRepository
import com.exponea.sdk.repository.EventRepositoryImpl
import com.exponea.sdk.repository.FirebaseTokenRepository
import com.exponea.sdk.repository.FirebaseTokenRepositoryImpl
import com.exponea.sdk.repository.InAppMessageDisplayStateRepositoryImpl
import com.exponea.sdk.repository.InAppMessagesCache
import com.exponea.sdk.repository.InAppMessagesCacheImpl
import com.exponea.sdk.repository.PushNotificationRepository
import com.exponea.sdk.repository.PushNotificationRepositoryImpl
import com.exponea.sdk.repository.UniqueIdentifierRepository
import com.exponea.sdk.repository.UniqueIdentifierRepositoryImpl
import com.exponea.sdk.util.currentTimeSeconds
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
internal class ExponeaComponent(
    exponeaConfiguration: ExponeaConfiguration,
    context: Context
) {
    // Gson Deserializer
    // - NaN and Infinity are serialized as strings, Gson fails to serialize them, it can do it but Exponea servers
    //   fail to process the JSON afterwards. This way devs know there is something going on and find the issue
    internal val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Double>() {}.type, JsonSerializer<Double> { src, _, _ ->
            if (src.isInfinite() || src.isNaN()) { JsonPrimitive(src.toString()) } else { JsonPrimitive(src) }
        })
        .registerTypeAdapter(object : TypeToken<Float>() {}.type, JsonSerializer<Float> { src, _, _ ->
            if (src.isInfinite() || src.isNaN()) { JsonPrimitive(src.toString()) } else { JsonPrimitive(src) }
        })
        .create()

    // Preferences
    internal val preferences: ExponeaPreferences = ExponeaPreferencesImpl(context)
    // Repositories
    internal val deviceInitiatedRepository: DeviceInitiatedRepository = DeviceInitiatedRepositoryImpl(
            preferences
    )
    private val uniqueIdentifierRepository: UniqueIdentifierRepository = UniqueIdentifierRepositoryImpl(
            preferences
    )

    internal val customerIdsRepository: CustomerIdsRepository = CustomerIdsRepositoryImpl(
            gson, uniqueIdentifierRepository, preferences
    )

    internal val pushNotificationRepository: PushNotificationRepository = PushNotificationRepositoryImpl(
            preferences
    )
    internal val eventRepository: EventRepository = EventRepositoryImpl()
    internal val firebaseTokenRepository: FirebaseTokenRepository = FirebaseTokenRepositoryImpl(preferences)
    internal val campaignRepository: CampaignRepository = CampaignRepositoryImpl(gson, preferences)
    internal val inAppMessagesCache: InAppMessagesCache = InAppMessagesCacheImpl(context, gson)
    internal val inAppMessageDisplayStateRepository = InAppMessageDisplayStateRepositoryImpl(preferences, gson)
    // Network Handler
    internal val networkManager: NetworkHandler = NetworkHandlerImpl(exponeaConfiguration)
    // Api Service
    internal val exponeaService: ExponeaService = ExponeaServiceImpl(gson, networkManager)

    // Managers
    internal val fetchManager: FetchManager = FetchManagerImpl(exponeaService)
    internal val backgroundTimerManager: BackgroundTimerManager =
        BackgroundTimerManagerImpl(context, exponeaConfiguration)
    internal val serviceManager: ServiceManager = ServiceManagerImpl()
    internal val connectionManager: ConnectionManager = ConnectionManagerImpl(context)
    internal val inAppMessageManager: InAppMessageManager
    init {
        inAppMessageManager = if (exponeaConfiguration.inAppMessagesEnabledBETA) {
            InAppMessageManagerImpl(
                context,
                exponeaConfiguration,
                customerIdsRepository,
                inAppMessagesCache,
                fetchManager,
                inAppMessageDisplayStateRepository
            )
        } else {
            DisabledInAppMessageManagerImpl()
        }
    }
    internal val eventManager: EventManager =
        EventManagerImpl(context, exponeaConfiguration, eventRepository, customerIdsRepository, inAppMessageManager)
    internal val flushManager: FlushManager =
        FlushManagerImpl(exponeaConfiguration, eventRepository, exponeaService, connectionManager)
    internal val fcmManager: FcmManager =
        FcmManagerImpl(context, exponeaConfiguration, firebaseTokenRepository, pushNotificationRepository)
    internal val fileManager: FileManager = FileManagerImpl()
    internal val personalizationManager: PersonalizationManager = PersonalizationManagerImpl(context)
    internal val sessionManager: SessionManager = SessionManagerImpl(context, preferences, eventManager)
    internal val iapManager: IapManager = IapManagerImpl(context, eventManager)

    fun anonymize() {
        val firebaseToken = Exponea.component.firebaseTokenRepository.get()
        fcmManager.trackFcmToken(" ")
        campaignRepository.clear()
        inAppMessagesCache.clear()
        inAppMessageDisplayStateRepository.clear()
        eventRepository.clear()
        uniqueIdentifierRepository.clear()
        customerIdsRepository.clear()
        sessionManager.reset()
        sessionManager.trackSessionStart(currentTimeSeconds())
        fcmManager.trackFcmToken(firebaseToken)
    }
}
