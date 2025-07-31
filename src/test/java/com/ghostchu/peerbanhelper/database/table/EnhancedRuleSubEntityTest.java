package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for Enhanced Rule Subscription database entities
 */
public class EnhancedRuleSubEntityTest {

    private EnhancedRuleSubInfoEntity infoEntity;
    private EnhancedRuleSubLogEntity logEntity;

    @BeforeEach
    public void setUp() {
        infoEntity = new EnhancedRuleSubInfoEntity();
        logEntity = new EnhancedRuleSubLogEntity();
    }

    @Test
    public void testEnhancedRuleSubInfoEntityCreation() {
        // Test basic entity creation and property setting
        infoEntity.setRuleId("test-rule-1");
        infoEntity.setRuleName("Test Rule");
        infoEntity.setRuleType(RuleType.IP_BLACKLIST);
        infoEntity.setSubUrl("https://example.com/rules.txt");
        infoEntity.setDescription("Test rule description");
        infoEntity.setEnabled(true);
        infoEntity.setEntCount(100);
        infoEntity.setLastUpdate(System.currentTimeMillis());

        assertEquals("test-rule-1", infoEntity.getRuleId());
        assertEquals("Test Rule", infoEntity.getRuleName());
        assertEquals(RuleType.IP_BLACKLIST, infoEntity.getRuleType());
        assertEquals("https://example.com/rules.txt", infoEntity.getSubUrl());
        assertEquals("Test rule description", infoEntity.getDescription());
        assertTrue(infoEntity.isEnabled());
        assertEquals(100, infoEntity.getEntCount());
        assertNotNull(infoEntity.getLastUpdate());
    }

    @Test
    public void testEnhancedRuleSubInfoEntityDefaults() {
        // Test default values
        assertNull(infoEntity.getRuleId());
        assertNull(infoEntity.getRuleName());
        assertNull(infoEntity.getRuleType());
        assertNull(infoEntity.getSubUrl());
        assertNull(infoEntity.getDescription());
        assertFalse(infoEntity.isEnabled()); // Should default to false
        assertEquals(0, infoEntity.getEntCount()); // Should default to 0
        assertNull(infoEntity.getLastUpdate());
    }

    @Test
    public void testEnhancedRuleSubInfoEntityAllRuleTypes() {
        // Test setting all different rule types
        for (RuleType ruleType : RuleType.values()) {
            infoEntity.setRuleType(ruleType);
            assertEquals(ruleType, infoEntity.getRuleType());
        }
    }

    @Test
    public void testEnhancedRuleSubLogEntityCreation() {
        // Test log entity creation and property setting
        logEntity.setRuleId("test-rule-1");
        logEntity.setRuleType(RuleType.SUBSTRING_MATCH);
        logEntity.setUpdateTime(System.currentTimeMillis());
        logEntity.setCount(50);
        logEntity.setUpdateType("MANUAL");
        logEntity.setStatus("SUCCESS");

        assertEquals("test-rule-1", logEntity.getRuleId());
        assertEquals(RuleType.SUBSTRING_MATCH, logEntity.getRuleType());
        assertNotNull(logEntity.getUpdateTime());
        assertEquals(50, logEntity.getCount());
        assertEquals("MANUAL", logEntity.getUpdateType());
        assertEquals("SUCCESS", logEntity.getStatus());
    }

    @Test
    public void testEnhancedRuleSubLogEntityDefaults() {
        // Test default values for log entity
        assertNull(logEntity.getRuleId());
        assertNull(logEntity.getRuleType());
        assertNull(logEntity.getUpdateTime());
        assertEquals(0, logEntity.getCount()); // Should default to 0
        assertNull(logEntity.getUpdateType());
        assertNull(logEntity.getStatus());
    }

    @Test
    public void testEntityValidation() {
        // Test that entities can handle null and empty values appropriately
        infoEntity.setRuleId("");
        infoEntity.setRuleName("");
        infoEntity.setSubUrl("");
        infoEntity.setDescription("");

        assertEquals("", infoEntity.getRuleId());
        assertEquals("", infoEntity.getRuleName());
        assertEquals("", infoEntity.getSubUrl());
        assertEquals("", infoEntity.getDescription());
    }

    @Test
    public void testEntityUpdateScenarios() {
        // Test common update scenarios
        
        // Initial setup
        infoEntity.setRuleId("rule-1");
        infoEntity.setRuleName("Initial Rule");
        infoEntity.setRuleType(RuleType.IP_BLACKLIST);
        infoEntity.setEnabled(false);
        infoEntity.setEntCount(0);
        
        // Enable rule
        infoEntity.setEnabled(true);
        assertTrue(infoEntity.isEnabled());
        
        // Update count after successful fetch
        infoEntity.setEntCount(250);
        infoEntity.setLastUpdate(System.currentTimeMillis());
        assertEquals(250, infoEntity.getEntCount());
        assertNotNull(infoEntity.getLastUpdate());
        
        // Change rule type
        infoEntity.setRuleType(RuleType.PEER_ID);
        assertEquals(RuleType.PEER_ID, infoEntity.getRuleType());
        
        // Reset count (e.g., after failed update)
        infoEntity.setEntCount(0);
        assertEquals(0, infoEntity.getEntCount());
    }

    @Test
    public void testLogEntityUpdateTypes() {
        // Test different update types
        String[] updateTypes = {"AUTOMATIC", "MANUAL", "STARTUP", "FORCED"};
        
        for (String updateType : updateTypes) {
            logEntity.setUpdateType(updateType);
            assertEquals(updateType, logEntity.getUpdateType());
        }
    }

    @Test
    public void testLogEntityStatuses() {
        // Test different status values
        String[] statuses = {"SUCCESS", "FAILED", "TIMEOUT", "ERROR", "INVALID_FORMAT"};
        
        for (String status : statuses) {
            logEntity.setStatus(status);
            assertEquals(status, logEntity.getStatus());
        }
    }

    @Test
    public void testEntityCloning() {
        // Setup original entity
        infoEntity.setRuleId("original-rule");
        infoEntity.setRuleName("Original Rule");
        infoEntity.setRuleType(RuleType.CLIENT_NAME);
        infoEntity.setSubUrl("https://original.com/rules");
        infoEntity.setDescription("Original description");
        infoEntity.setEnabled(true);
        infoEntity.setEntCount(75);
        long timestamp = System.currentTimeMillis();
        infoEntity.setLastUpdate(timestamp);

        // Create new entity with same data (simulating clone operation)
        EnhancedRuleSubInfoEntity clonedEntity = new EnhancedRuleSubInfoEntity();
        clonedEntity.setRuleId(infoEntity.getRuleId());
        clonedEntity.setRuleName(infoEntity.getRuleName());
        clonedEntity.setRuleType(infoEntity.getRuleType());
        clonedEntity.setSubUrl(infoEntity.getSubUrl());
        clonedEntity.setDescription(infoEntity.getDescription());
        clonedEntity.setEnabled(infoEntity.isEnabled());
        clonedEntity.setEntCount(infoEntity.getEntCount());
        clonedEntity.setLastUpdate(infoEntity.getLastUpdate());

        // Verify all properties match
        assertEquals(infoEntity.getRuleId(), clonedEntity.getRuleId());
        assertEquals(infoEntity.getRuleName(), clonedEntity.getRuleName());
        assertEquals(infoEntity.getRuleType(), clonedEntity.getRuleType());
        assertEquals(infoEntity.getSubUrl(), clonedEntity.getSubUrl());
        assertEquals(infoEntity.getDescription(), clonedEntity.getDescription());
        assertEquals(infoEntity.isEnabled(), clonedEntity.isEnabled());
        assertEquals(infoEntity.getEntCount(), clonedEntity.getEntCount());
        assertEquals(infoEntity.getLastUpdate(), clonedEntity.getLastUpdate());
    }

    @Test
    public void testLargeDataValues() {
        // Test handling of large values
        infoEntity.setEntCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, infoEntity.getEntCount());
        
        infoEntity.setLastUpdate(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, infoEntity.getLastUpdate());
        
        logEntity.setCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, logEntity.getCount());
        
        logEntity.setUpdateTime(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, logEntity.getUpdateTime());
    }

    @Test
    public void testNegativeValues() {
        // Test that negative values are handled appropriately
        infoEntity.setEntCount(-1);
        assertEquals(-1, infoEntity.getEntCount());
        
        logEntity.setCount(-1);
        assertEquals(-1, logEntity.getCount());
    }

    @Test
    public void testLongStringValues() {
        // Test handling of long strings
        String longString = "a".repeat(1000);
        
        infoEntity.setRuleId(longString);
        assertEquals(longString, infoEntity.getRuleId());
        
        infoEntity.setRuleName(longString);
        assertEquals(longString, infoEntity.getRuleName());
        
        infoEntity.setDescription(longString);
        assertEquals(longString, infoEntity.getDescription());
        
        logEntity.setStatus(longString);
        assertEquals(longString, logEntity.getStatus());
    }
}