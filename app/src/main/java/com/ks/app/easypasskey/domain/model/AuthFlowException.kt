package com.ks.app.easypasskey.domain.model

/** 認証フローの失敗(認可エラー・state不一致・トークン交換失敗など) */
class AuthFlowException(message: String) : Exception(message)
