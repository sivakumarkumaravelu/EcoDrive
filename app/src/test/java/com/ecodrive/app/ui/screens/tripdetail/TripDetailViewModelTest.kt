package com.ecodrive.app.ui.screens.tripdetail

import androidx.lifecycle.SavedStateHandle
import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.AiInsightDao
import com.ecodrive.app.data.local.dao.DataPointDao
import com.ecodrive.app.data.local.entity.DataPointEntity
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.analyzer.LocalEcoCoach
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.Trip
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailViewModelTest {

    private val tripRepository: TripRepository = mockk(relaxed = true)
    private val dataPointDao: DataPointDao = mockk(relaxed = true)
    private val aiInsightDao: AiInsightDao = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val localEcoCoach: LocalEcoCoach = mockk(relaxed = true)
    private val aiManager: AiManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val dataPointsFlow = MutableStateFlow<List<DataPointEntity>>(emptyList())
    private val eventsFlow = MutableStateFlow<List<DrivingEvent>>(emptyList())

    private lateinit var viewModel: TripDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()

        every { dataPointDao.getDataPointsForTrip(any()) } returns dataPointsFlow
        every { tripRepository.getEventsForTrip(any()) } returns eventsFlow
        coEvery { tripRepository.getTripById(any()) } returns Trip(
            id = 1L,
            startTime = Instant.now(),
            isActive = false,
            ecoScore = 85
        )

        val savedStateHandle = SavedStateHandle(mapOf("tripId" to 1L))
        viewModel = TripDetailViewModel(
            savedStateHandle,
            tripRepository,
            dataPointDao,
            aiInsightDao,
            preferenceManager,
            localEcoCoach,
            aiManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test trip loaded from repository`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value
        assertNotNull(state.trip)
        assertEquals(1L, state.trip?.id)
        assertEquals(85, state.trip?.ecoScore)
    }

    @Test
    fun `test isLoading becomes false when data points is empty`() = runTest {
        dataPointsFlow.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `test chart data populated from data points`() = runTest {
        val baseTime = System.currentTimeMillis()
        val points = listOf(
            DataPointEntity(
                tripId = 1L,
                timestampEpochMs = baseTime,
                speedKmh = 50.0,
                longitudinalAccelMps2 = 0.5,
                fuelConsumptionLPer100Km = 7.0,
                altitudeM = 100.0,
                latitude = 10.0,
                longitude = 20.0
            ),
            DataPointEntity(
                tripId = 1L,
                timestampEpochMs = baseTime + 60_000,
                speedKmh = 70.0,
                longitudinalAccelMps2 = -0.5,
                fuelConsumptionLPer100Km = 8.0,
                altitudeM = 110.0,
                latitude = 10.1,
                longitude = 20.1
            ),
        )
        dataPointsFlow.value = points
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.speedPoints.size)
        assertEquals(2, state.accelPoints.size)
        assertEquals(2, state.fuelPoints.size)
        assertEquals(2, state.altitudePoints.size)
        assertEquals(2, state.routePoints.size)

        // Speed values should be mapped correctly
        assertEquals(50.0f, state.speedPoints[0].y, 0.001f)
        assertEquals(70.0f, state.speedPoints[1].y, 0.001f)
    }

    @Test
    fun `test events loaded from repository`() = runTest {
        val events = listOf(
            DrivingEvent(id = 1L, tripId = 1L, type = DrivingEventType.HARD_BRAKE),
            DrivingEvent(id = 2L, tripId = 1L, type = DrivingEventType.HARD_ACCELERATION),
        )
        eventsFlow.value = events
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.events.size)
    }

    @Test
    fun `test route points exclude zero coordinates`() = runTest {
        val baseTime = System.currentTimeMillis()
        val points = listOf(
            DataPointEntity(
                tripId = 1L,
                timestampEpochMs = baseTime,
                speedKmh = 50.0,
                latitude = 0.0,  // should be excluded
                longitude = 0.0
            ),
            DataPointEntity(
                tripId = 1L,
                timestampEpochMs = baseTime + 60_000,
                speedKmh = 60.0,
                latitude = 10.1,
                longitude = 20.1
            ),
        )
        dataPointsFlow.value = points
        advanceUntilIdle()

        // Only the second point with valid coordinates should appear
        assertEquals(1, viewModel.state.value.routePoints.size)
    }
}
