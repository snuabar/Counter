package com.snuabar.counter.di

import com.snuabar.counter.core.detection.DetectionEngineFactory
import com.snuabar.counter.core.detection.DetectionEngineFactoryImpl
import com.snuabar.counter.data.repository.CountingSessionRepositoryImpl
import com.snuabar.counter.data.repository.TemplateRepositoryImpl
import com.snuabar.counter.data.repository.UserRepositoryImpl
import com.snuabar.counter.domain.repository.CountingSessionRepository
import com.snuabar.counter.domain.repository.TemplateRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindCountingSessionRepository(
        impl: CountingSessionRepositoryImpl
    ): CountingSessionRepository

    @Binds
    abstract fun bindTemplateRepository(
        impl: TemplateRepositoryImpl
    ): TemplateRepository
    @Binds
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    abstract fun bindDetectionEngineFactory(
        impl: DetectionEngineFactoryImpl
    ): DetectionEngineFactory
}
