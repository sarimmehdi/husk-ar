package com.sarim.husk.session.data.repository

import com.sarim.husk.session.domain.repository.SessionRepository

class InMemorySessionRepositoryImplTest : SessionRepositoryContract() {
    override fun createRepository(): SessionRepository = InMemorySessionRepositoryImpl()
}
