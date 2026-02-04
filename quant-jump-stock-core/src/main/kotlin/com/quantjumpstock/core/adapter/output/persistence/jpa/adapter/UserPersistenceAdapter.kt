package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.UserMapper
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * User Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val userMapper: UserMapper
) : UserRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(user: User): User {
        logger.debug("사용자 저장: userId=${user.userId}")
        val entity = userMapper.toEntity(user)
        val savedEntity = userJpaRepository.save(entity)
        return userMapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): User? {
        logger.debug("사용자 조회: id=$id")
        return userJpaRepository.findById(id)
            .map { userMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByUserId(userId: String): User? {
        logger.debug("사용자 조회: userId=$userId")
        return userJpaRepository.findByUserId(userId)
            .map { userMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByEmail(email: String): User? {
        logger.debug("사용자 조회: email=$email")
        return userJpaRepository.findByEmail(email)
            .map { userMapper.toDomain(it) }
            .orElse(null)
    }

    override fun existsById(id: Long): Boolean {
        return userJpaRepository.existsById(id)
    }

    override fun existsByUserId(userId: String): Boolean {
        return userJpaRepository.existsByUserId(userId)
    }

    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }

    override fun deleteById(id: Long) {
        logger.debug("사용자 삭제: id=$id")
        userJpaRepository.deleteById(id)
    }
}
