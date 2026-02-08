package com.openclaw.dashboard.util

/**
 * Utility functions for processing text content from AI responses.
 * Mirrors the logic from OpenClaw's reasoning-tags.ts
 */
object TextUtils {
    
    // Quick check regex to see if text might contain thinking tags
    private val QUICK_TAG_REGEX = Regex("<\\s*/?\\s*(?:think(?:ing)?|thought|antthinking|final)\\b", RegexOption.IGNORE_CASE)
    
    // Regex to match <final> tags
    private val FINAL_TAG_REGEX = Regex("<\\s*/?\\s*final\\b[^<>]*>", RegexOption.IGNORE_CASE)
    
    // Regex to match thinking tags (capturing if it's a closing tag)
    private val THINKING_TAG_REGEX = Regex("<\\s*(/?)?\\s*(?:think(?:ing)?|thought|antthinking)\\b[^<>]*>", RegexOption.IGNORE_CASE)
    
    // Regex for fenced code blocks
    private val FENCED_CODE_REGEX = Regex("(^|\\n)(```|~~~)[^\\n]*\\n[\\s\\S]*?(?:\\n\\2(?:\\n|$)|$)")
    
    // Regex for inline code
    private val INLINE_CODE_REGEX = Regex("`+[^`]+`+")
    
    data class CodeRegion(val start: Int, val end: Int)
    
    /**
     * Find all code regions (fenced and inline) in the text.
     * These regions should be excluded from thinking tag processing.
     */
    private fun findCodeRegions(text: String): List<CodeRegion> {
        val regions = mutableListOf<CodeRegion>()
        
        // Find fenced code blocks
        FENCED_CODE_REGEX.findAll(text).forEach { match ->
            val start = match.range.first + (match.groups[1]?.value?.length ?: 0)
            regions.add(CodeRegion(start, match.range.last + 1))
        }
        
        // Find inline code (excluding those inside fenced blocks)
        INLINE_CODE_REGEX.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val insideFenced = regions.any { start >= it.start && end <= it.end }
            if (!insideFenced) {
                regions.add(CodeRegion(start, end))
            }
        }
        
        return regions.sortedBy { it.start }
    }
    
    /**
     * Check if a position is inside a code region.
     */
    private fun isInsideCode(pos: Int, regions: List<CodeRegion>): Boolean {
        return regions.any { pos >= it.start && pos < it.end }
    }
    
    /**
     * Strip thinking/reasoning tags from text.
     * This removes <thinking>...</thinking>, <antthinking>...</antthinking>, 
     * <thought>...</thought>, <think>...</think> tags and their content.
     * 
     * Code blocks are preserved - thinking tags inside code are not stripped.
     * 
     * @param text The input text that may contain thinking tags
     * @param preserveIncomplete If true, preserve content after unclosed thinking tags
     * @param trimStart If true, trim whitespace from the start of the result
     * @return Text with thinking tags and their content removed
     */
    fun stripThinkingTags(
        text: String,
        preserveIncomplete: Boolean = true,
        trimStart: Boolean = true
    ): String {
        if (text.isEmpty()) return text
        
        // Quick check - if no thinking tags, return as-is
        if (!QUICK_TAG_REGEX.containsMatchIn(text)) return text
        
        var cleaned = text
        
        // Remove <final> tags first (but keep their content)
        if (FINAL_TAG_REGEX.containsMatchIn(cleaned)) {
            val preCodeRegions = findCodeRegions(cleaned)
            val finalMatches = FINAL_TAG_REGEX.findAll(cleaned).toList().reversed()
            
            for (match in finalMatches) {
                if (!isInsideCode(match.range.first, preCodeRegions)) {
                    cleaned = cleaned.removeRange(match.range)
                }
            }
        }
        
        val codeRegions = findCodeRegions(cleaned)
        
        val result = StringBuilder()
        var lastIndex = 0
        var inThinking = false
        
        for (match in THINKING_TAG_REGEX.findAll(cleaned)) {
            val idx = match.range.first
            val isClose = match.groups[1]?.value == "/"
            
            // Skip tags inside code blocks
            if (isInsideCode(idx, codeRegions)) {
                continue
            }
            
            if (!inThinking) {
                // Not currently inside thinking block
                result.append(cleaned.substring(lastIndex, idx))
                if (!isClose) {
                    inThinking = true
                }
            } else if (isClose) {
                // Closing a thinking block
                inThinking = false
            }
            // Skip everything until the end of this tag
            lastIndex = match.range.last + 1
        }
        
        // Append remaining content
        if (!inThinking || preserveIncomplete) {
            result.append(cleaned.substring(lastIndex))
        }
        
        return if (trimStart) result.toString().trimStart() else result.toString()
    }
}
