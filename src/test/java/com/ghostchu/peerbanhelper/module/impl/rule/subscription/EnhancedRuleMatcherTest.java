package com.ghostchu.peerbanhelper.module.impl.rule.subscription;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.SubstringMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.PrefixMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.ClientNameMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for Enhanced Rule Subscription matchers
 */
public class EnhancedRuleMatcherTest {
    
    @Test
    public void testSubstringMatcher() {
        List<String> patterns = Arrays.asList("torrent", "download", "bad");
        SubstringMatcher matcher = new SubstringMatcher("test-rule", "Test Substring Rule", patterns);
        
        // Test positive matches
        MatchResult result1 = matcher.match("BitTorrent 7.10");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        MatchResult result2 = matcher.match("bad-client-1.0");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        // Test negative matches
        MatchResult result3 = matcher.match("qBittorrent 4.5.0");
        assertEquals(MatchResultEnum.FALSE, result3.result());
        
        MatchResult result4 = matcher.match("Transmission 3.0");
        assertEquals(MatchResultEnum.FALSE, result4.result());
        
        // Test null/empty
        MatchResult result5 = matcher.match(null);
        assertEquals(MatchResultEnum.FALSE, result5.result());
        
        MatchResult result6 = matcher.match("");
        assertEquals(MatchResultEnum.FALSE, result6.result());
    }
    
    @Test
    public void testPrefixMatcher() {
        List<String> prefixes = Arrays.asList("Fake", "Thunder", "Xunlei");
        PrefixMatcher matcher = new PrefixMatcher("test-prefix", "Test Prefix Rule", prefixes);
        
        // Test positive matches
        MatchResult result1 = matcher.match("FakeClient 1.0");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        MatchResult result2 = matcher.match("Thunder 7.10.35.366");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        MatchResult result3 = matcher.match("Xunlei/1.0");
        assertEquals(MatchResultEnum.TRUE, result3.result());
        
        // Test negative matches
        MatchResult result4 = matcher.match("qBittorrent 4.5.0");
        assertEquals(MatchResultEnum.FALSE, result4.result());
        
        MatchResult result5 = matcher.match("Real Thunder Client");
        assertEquals(MatchResultEnum.FALSE, result5.result());
    }
    
    @Test
    public void testClientNameMatcher() {
        List<String> clientNames = Arrays.asList("BitComet 1.68", "uTorrent 3.5.5", "Vuze 5.7.6.0");
        ClientNameMatcher matcher = new ClientNameMatcher("test-client", "Test Client Rule", clientNames);
        
        // Test positive matches
        MatchResult result1 = matcher.match("BitComet 1.68");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        MatchResult result2 = matcher.match("uTorrent 3.5.5");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        // Test negative matches - similar but not exact
        MatchResult result3 = matcher.match("BitComet 1.69");
        assertEquals(MatchResultEnum.FALSE, result3.result());
        
        MatchResult result4 = matcher.match("qBittorrent 4.5.0");
        assertEquals(MatchResultEnum.FALSE, result4.result());
    }
    
    @Test
    public void testRuleTypeEnum() {
        // Test enum conversion
        assertEquals(RuleType.IP_BLACKLIST, RuleType.fromCode("ip_blacklist"));
        assertEquals(RuleType.SUBSTRING_MATCH, RuleType.fromCode("substring_match"));
        assertEquals(RuleType.PREFIX_MATCH, RuleType.fromCode("prefix_match"));
        
        // Test code generation
        assertEquals("ip_blacklist", RuleType.IP_BLACKLIST.getCode());
        assertEquals("peer_id", RuleType.PEER_ID.getCode());
        assertEquals("client_name", RuleType.CLIENT_NAME.getCode());
        
        // Test memory optimization requirement
        assertTrue(RuleType.IP_BLACKLIST.requiresMemoryOptimization());
        assertFalse(RuleType.SUBSTRING_MATCH.requiresMemoryOptimization());
        assertFalse(RuleType.PREFIX_MATCH.requiresMemoryOptimization());
        
        // Test invalid code
        assertThrows(IllegalArgumentException.class, () -> {
            RuleType.fromCode("invalid_type");
        });
    }
    
    @Test
    public void testMatcherDataUpdate() {
        List<String> initialPatterns = Arrays.asList("pattern1", "pattern2");
        SubstringMatcher matcher = new SubstringMatcher("test-update", "Test Update", initialPatterns);
        
        // Test initial data
        assertEquals(2, matcher.getDataSize());
        
        MatchResult result1 = matcher.match("pattern1-test");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        // Update data
        List<String> newPatterns = Arrays.asList("newpattern1", "newpattern2", "newpattern3");
        matcher.setData("Updated Rule", List.of(newPatterns));
        
        // Test updated data
        assertEquals(3, matcher.getDataSize());
        assertEquals("Updated Rule", matcher.getRuleName());
        
        // Old patterns should not match anymore
        MatchResult result2 = matcher.match("pattern1-test");
        assertEquals(MatchResultEnum.FALSE, result2.result());
        
        // New patterns should match
        MatchResult result3 = matcher.match("newpattern1-test");
        assertEquals(MatchResultEnum.TRUE, result3.result());
    }
}