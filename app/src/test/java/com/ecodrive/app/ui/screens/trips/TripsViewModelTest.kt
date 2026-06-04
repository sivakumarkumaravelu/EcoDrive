package com.ecodrive.app.ui.screens.trips

import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.Trip
import com.google.android.gms.maps.model.LatLng
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
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {

    private val tripRepository: TripRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())
    private val routesFlow = MutableStateFlow<Map<Long, List<LatLng>>>(emptyMap())

    private lateinit var viewModel: TripsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()

        every { tripRepository.getRecentTrips(any()) } returns tripsFlow
        every { tripRepository.getRoutePointsForTrips(any()) } returns routesFlow
        coEvery { tripRepository.getAverageEcoScore(any()) } returns 75.0
        coEvery { tripRepository.getTotalDistance(any()) } returns 200.0
        coEvery { tripRepository.getTotalFuelConsumed(any()) } returns 12.5

        viewModel = TripsViewModel(tripRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state is loading`() {
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `test state reflects loaded trips`() = runTest {
        val trips = listOf(
            Trip(id = 1, startTime = Instant.now(), isActive = false, ecoScore = 80),
            Trip(id = 2, startTime = Instant.now(), isActive = false, ecoScore = 65),
        )
        tripsFlow.value = trips
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.trips.size)
        assertEquals(2, state.totalTrips)
    }

    @Test
    fun `test weekly stats loaded on init`() = runTest {
        tripsFlow.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(75, state.weeklyAvgScore)
        assertEquals(200.0, state.weeklyDistance, 0.001)
        assertEquals(12.5, state.weeklyFuel, 0.001)
    }

    @Test
    fun `test route points loaded when trips exist`() = runTest {
        val trips = listOf(Trip(id = 1, startTime = Instant.now(), isActive = false))
        tripsFlow.value = trips

        val routes = mapOf(1L to listOf(LatLng(10.0, 20.0), LatLng(10.1, 20.1)))
        routesFlow.value = routes
        advanceUntilIdle()

        assertEquals(routes, viewModel.state.value.tripRoutes)
    }

    @Test
    fun `test deleteTrip calls repository`() = runTest {
        viewModel.deleteTrip(tripId = 5L)
        advanceUntilIdle()
        coVerify { tripRepository.deleteTrip(5L) }
    }

    @Test
    fun `test refresh reloads trips`() = runTest {
        tripsFlow.value = emptyList()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // Verify repository was called again
        verify(atLeast = 2) { tripRepository.getRecentTrips(any()) }
    }
}
