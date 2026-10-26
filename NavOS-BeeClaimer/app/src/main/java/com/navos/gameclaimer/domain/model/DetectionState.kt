package com.navos.gameclaimer.domain.model

/**
 * Phase 18D: Generic detection state returned by all store adapters.
 * 
 * Per GPT spec: Explicit states to distinguish ambiguous conditions.
 * Never interpret an ambiguous result as success.
 */
enum class DetectionState {
    /** Item is available for claiming/adding to library */
    AVAILABLE,
    
    /** Item is already owned/in library */
    OWNED,
    
    /** Item not found at URL */
    NOT_FOUND,
    
    /** Authentication required to check ownership */
    AUTH_REQUIRED,
    
    /** Multi-factor authentication screen detected */
    MFA_REQUIRED,
    
    /** Cookie consent overlay blocking detection */
    CONSENT_REQUIRED,
    
    /** Region/platform restriction detected */
    REGION_RESTRICTED,
    
    /** CAPTCHA challenge detected */
    CAPTCHA_REQUIRED,
    
    /** User is not eligible (e.g., level requirement, subscription) */
    NOT_ELIGIBLE,
    
    /** Item is not free (requires payment) */
    NOT_FREE,
    
    /** Giveaway/offer has expired */
    EXPIRED,
    
    /** Page is still loading or in transitional state */
    LOADING,
    
    /** Detection timeout */
    TIMEOUT,
    
    /** Error during detection */
    ERROR,
    
    /** Cannot determine state - do not assume success */
    UNKNOWN
}

/**
 * Result of a detection operation with context.
 * 
 * @property state The detected state
 * @property storeSpecificState Optional store-specific state (e.g., SteamOwnershipState)
 * @property rawResult The raw JS result for debugging
 * @property detectionMethod How ownership/availability was detected
 * @property errorMessage Error message if state is ERROR
 * @property pageElements Elements found on page (for UNKNOWN state diagnosis)
 */
data class DetectionResult(
    val state: DetectionState,
    val storeSpecificState: Any? = null,
    val rawResult: String? = null,
    val detectionMethod: String? = null,
    val errorMessage: String? = null,
    val pageElements: List<String> = emptyList()
) {
    companion object {
        fun available(method: String, raw: String? = null) = DetectionResult(
            state = DetectionState.AVAILABLE,
            detectionMethod = method,
            rawResult = raw
        )
        
        fun owned(method: String, raw: String? = null) = DetectionResult(
            state = DetectionState.OWNED,
            detectionMethod = method,
            rawResult = raw
        )
        
        fun authRequired(reason: String? = null) = DetectionResult(
            state = DetectionState.AUTH_REQUIRED,
            errorMessage = reason
        )
        
        fun mfaRequired() = DetectionResult(
            state = DetectionState.MFA_REQUIRED
        )
        
        fun consentRequired() = DetectionResult(
            state = DetectionState.CONSENT_REQUIRED
        )
        
        fun regionRestricted() = DetectionResult(
            state = DetectionState.REGION_RESTRICTED
        )
        
        fun captchaRequired() = DetectionResult(
            state = DetectionState.CAPTCHA_REQUIRED
        )
        
        fun loading() = DetectionResult(
            state = DetectionState.LOADING
        )
        
        fun timeout() = DetectionResult(
            state = DetectionState.TIMEOUT
        )
        
        fun error(message: String, raw: String? = null) = DetectionResult(
            state = DetectionState.ERROR,
            errorMessage = message,
            rawResult = raw
        )
        
        fun unknown(pageElements: List<String> = emptyList(), raw: String? = null) = DetectionResult(
            state = DetectionState.UNKNOWN,
            pageElements = pageElements,
            rawResult = raw
        )
        
        fun notFound() = DetectionResult(
            state = DetectionState.NOT_FOUND
        )
    }
}
