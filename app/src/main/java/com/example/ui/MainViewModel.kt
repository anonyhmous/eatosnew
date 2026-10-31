package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AytosRepository
import com.example.model.*
import com.example.ui.theme.ThemeState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: AytosRepository = AytosRepository.getInstance(application)

    val isReaderActive: StateFlow<Boolean> = repository.preferences.isReaderActiveFlow
    val driverProfile: StateFlow<DriverProfile> = repository.preferences.profileFlow
    val driverRules: StateFlow<DriverRules> = repository.preferences.rulesFlow
    val themeMode: StateFlow<Int> = repository.preferences.themeModeFlow
    val todaySummary: StateFlow<ShiftSummary> = repository.todaySummary
    val latestJudgment: StateFlow<JudgmentResult?> = repository.latestJudgment
    val isOverlayVisible: StateFlow<Boolean> = repository.isOverlayVisible
    val hasCompletedOnboarding: StateFlow<Boolean> = repository.preferences.hasCompletedOnboarding

    val preferredZones: StateFlow<List<ZoneItem>> = repository.preferredZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val avoidedZones: StateFlow<List<ZoneItem>> = repository.avoidedZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allZones: StateFlow<List<ZoneItem>> = repository.allZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _showSimulatorDialog = MutableStateFlow(false)
    val showSimulatorDialog: StateFlow<Boolean> = _showSimulatorDialog.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setShowSimulator(show: Boolean) {
        _showSimulatorDialog.value = show
    }

    fun setReaderActive(active: Boolean) {
        repository.preferences.setReaderActive(active)
    }

    fun completeOnboarding(profile: DriverProfile) {
        repository.preferences.updateProfile(profile)
        repository.preferences.setCompletedOnboarding(true)
    }

    fun updateRules(rules: DriverRules) {
        repository.preferences.updateRules(rules)
    }

    fun updateProfile(profile: DriverProfile) {
        repository.preferences.updateProfile(profile)
    }

    fun toggleGoingHomeMode() {
        val current = driverRules.value
        val updated = current.copy(isGoingHomeMode = !current.isGoingHomeMode, isGoingDestMode = false)
        repository.preferences.updateRules(updated)
    }

    fun toggleGoingDestMode() {
        val current = driverRules.value
        val updated = current.copy(isGoingDestMode = !current.isGoingDestMode, isGoingHomeMode = false)
        repository.preferences.updateRules(updated)
    }

    fun addZone(name: String, governorate: String, type: ZoneType, notes: String) {
        viewModelScope.launch {
            repository.addZone(name, governorate, type, notes)
        }
    }

    fun deleteZone(zone: ZoneItem) {
        viewModelScope.launch {
            repository.deleteZone(zone)
        }
    }

    /** تحويل نهاري <-> ليلي: بيثبّت الاختيار ويفضل محفوظ. */
    fun toggleTheme() {
        val newMode = if (ThemeState.isDark) 1 else 2
        ThemeState.mode = newMode
        repository.preferences.setThemeMode(newMode)
    }

    /** بيرجّع آخر نص اتقرا من الشاشة (للتشخيص). */
    fun lastCaptureText(): String = repository.lastCapture

    fun dismissOverlay() {
        repository.dismissOverlay(byUser = true)
    }

    /**
     * Interactive Live Simulator:
     * Injects a mock ride offer card into the judgment engine so driver can test
     * Uber, DiDi, inDrive cards, calculation rules, and floating overlay in real time.
     */
    fun simulateCard(
        app: SupportedApp,
        fare: Double,
        pickupKm: Double,
        tripKm: Double,
        passengerRating: Double = 4.88,
        passengerTrips: Int = 42,
        orderAgeSec: Int = 12,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        stopsCount: Int = 1,
        pickupAddr: String = "المهندسين - شارع جامعة الدول",
        dropoffAddr: String = "التجمع الخامس - شارع التسعين",
        note: String? = null
    ) {
        val order = OrderDetails(
            app = app,
            rawText = "طلب $app - $fare ج.م - استلام $pickupKm كم - رحلة $tripKm كم",
            fare = fare,
            pickupDistanceKm = pickupKm,
            tripDistanceKm = tripKm,
            passengerRating = passengerRating,
            passengerTripsCount = passengerTrips,
            orderAgeSeconds = orderAgeSec,
            paymentMethod = paymentMethod,
            stopsCount = stopsCount,
            pickupAddress = pickupAddr,
            dropoffAddress = dropoffAddr,
            passengerNote = note
        )
        repository.processOrder(order, record = false)
    }
}
