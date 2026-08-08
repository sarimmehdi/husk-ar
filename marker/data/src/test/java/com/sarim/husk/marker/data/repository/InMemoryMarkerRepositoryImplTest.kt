package com.sarim.husk.marker.data.repository

import com.sarim.husk.marker.domain.repository.MarkerRepository

class InMemoryMarkerRepositoryImplTest : MarkerRepositoryContract() {
    override fun createRepository(): MarkerRepository = InMemoryMarkerRepositoryImpl()
}
