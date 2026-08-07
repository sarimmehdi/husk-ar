package com.sarim.husk.starter.data.repository

import com.sarim.husk.starter.domain.repository.GreetingRepository

class InMemoryGreetingRepositoryImplTest : GreetingRepositoryContract() {
    override fun createRepository(): GreetingRepository = InMemoryGreetingRepositoryImpl()
}
