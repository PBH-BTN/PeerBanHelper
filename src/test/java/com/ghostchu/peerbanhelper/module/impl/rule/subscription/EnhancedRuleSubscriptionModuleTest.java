package com.ghostchu.peerbanhelper.module.impl.rule.subscription;

import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubInfoEntity;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.EnhancedIPMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.SubstringMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.PrefixMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.ClientNameMatcher;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.PeerIdMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test cases for Enhanced Rule Subscription Module
 */
public class EnhancedRuleSubscriptionModuleTest {

    @Mock
    private EnhancedRuleSubInfoEntity mockEntity;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRuleTypeMemoryOptimization() {
        // Test which rule types require memory optimization
        assertTrue(RuleType.IP_BLACKLIST.requiresMemoryOptimization());
        assertFalse(RuleType.PEER_ID.requiresMemoryOptimization());
        assertFalse(RuleType.CLIENT_NAME.requiresMemoryOptimization());
        assertFalse(RuleType.SUBSTRING_MATCH.requiresMemoryOptimization());
        assertFalse(RuleType.PREFIX_MATCH.requiresMemoryOptimization());
        assertFalse(RuleType.EXCEPTION_LIST.requiresMemoryOptimization());
        assertFalse(RuleType.SCRIPT_ENGINE.requiresMemoryOptimization());
    }

    @Test
    public void testRuleTypeCodeMapping() {
        // Test all rule type codes
        assertEquals("ip_blacklist", RuleType.IP_BLACKLIST.getCode());
        assertEquals("peer_id", RuleType.PEER_ID.getCode());
        assertEquals("client_name", RuleType.CLIENT_NAME.getCode());
        assertEquals("substring_match", RuleType.SUBSTRING_MATCH.getCode());
        assertEquals("prefix_match", RuleType.PREFIX_MATCH.getCode());
        assertEquals("exception_list", RuleType.EXCEPTION_LIST.getCode());
        assertEquals("script_engine", RuleType.SCRIPT_ENGINE.getCode());
    }

    @Test
    public void testRuleTypeFromCode() {
        // Test fromCode method for all types
        assertEquals(RuleType.IP_BLACKLIST, RuleType.fromCode("ip_blacklist"));
        assertEquals(RuleType.PEER_ID, RuleType.fromCode("peer_id"));
        assertEquals(RuleType.CLIENT_NAME, RuleType.fromCode("client_name"));
        assertEquals(RuleType.SUBSTRING_MATCH, RuleType.fromCode("substring_match"));
        assertEquals(RuleType.PREFIX_MATCH, RuleType.fromCode("prefix_match"));
        assertEquals(RuleType.EXCEPTION_LIST, RuleType.fromCode("exception_list"));
        assertEquals(RuleType.SCRIPT_ENGINE, RuleType.fromCode("script_engine"));
    }

    @Test
    public void testInvalidRuleTypeCode() {
        assertThrows(IllegalArgumentException.class, () -> {
            RuleType.fromCode("invalid_code");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            RuleType.fromCode(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            RuleType.fromCode("");
        });
    }

    @Test
    public void testPeerIdMatcher() {
        List<String> peerIds = Arrays.asList("-TR2940-", "-UT3500-", "-QR4200-");
        PeerIdMatcher matcher = new PeerIdMatcher("test-peer", "Test PeerID Rule", peerIds);
        
        // Test exact matches
        MatchResult result1 = matcher.match("-TR2940-");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        MatchResult result2 = matcher.match("-UT3500-");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        // Test non-matches
        MatchResult result3 = matcher.match("-TR2941-");
        assertEquals(MatchResultEnum.FALSE, result3.result());
        
        MatchResult result4 = matcher.match("different-peer-id");
        assertEquals(MatchResultEnum.FALSE, result4.result());
        
        // Test null/empty
        MatchResult result5 = matcher.match(null);
        assertEquals(MatchResultEnum.FALSE, result5.result());
        
        MatchResult result6 = matcher.match("");
        assertEquals(MatchResultEnum.FALSE, result6.result());
    }

    @Test
    public void testMatcherCaseSensitivity() {
        // Test case sensitivity for different matcher types
        List<String> patterns = Arrays.asList("BitTorrent", "THUNDER");
        
        // Substring matcher should be case-insensitive
        SubstringMatcher substringMatcher = new SubstringMatcher("test-sub", "Test", patterns);
        MatchResult result1 = substringMatcher.match("bittorrent client");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        // Prefix matcher should be case-insensitive
        PrefixMatcher prefixMatcher = new PrefixMatcher("test-prefix", "Test", patterns);
        MatchResult result2 = prefixMatcher.match("thunder 7.10");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        // Client name matcher should be exact (case-sensitive)
        ClientNameMatcher clientMatcher = new ClientNameMatcher("test-client", "Test", patterns);
        MatchResult result3 = clientMatcher.match("bittorrent");
        assertEquals(MatchResultEnum.FALSE, result3.result());
        
        MatchResult result4 = clientMatcher.match("BitTorrent");
        assertEquals(MatchResultEnum.TRUE, result4.result());
    }

    @Test
    public void testMatcherDataSizes() {
        List<String> smallData = Arrays.asList("item1", "item2");
        List<String> largeData = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        
        SubstringMatcher matcher = new SubstringMatcher("test", "Test", smallData);
        assertEquals(2, matcher.getDataSize());
        
        // Update with larger dataset
        matcher.setData("Updated Test", List.of(largeData));
        assertEquals(5, matcher.getDataSize());
        
        // Update with empty dataset
        matcher.setData("Empty Test", List.of(Arrays.asList()));
        assertEquals(0, matcher.getDataSize());
    }

    @Test
    public void testMatcherRuleNameUpdate() {
        List<String> patterns = Arrays.asList("test");
        SubstringMatcher matcher = new SubstringMatcher("test-id", "Original Name", patterns);
        
        assertEquals("Original Name", matcher.getRuleName());
        
        matcher.setData("Updated Name", List.of(patterns));
        assertEquals("Updated Name", matcher.getRuleName());
        
        matcher.setData("Final Name", List.of(Arrays.asList()));
        assertEquals("Final Name", matcher.getRuleName());
    }

    @Test
    public void testMatcherEmptyPatterns() {
        // Test matchers with empty pattern lists
        List<String> emptyPatterns = Arrays.asList();
        
        SubstringMatcher substringMatcher = new SubstringMatcher("test", "Test", emptyPatterns);
        MatchResult result1 = substringMatcher.match("any text");
        assertEquals(MatchResultEnum.FALSE, result1.result());
        
        PrefixMatcher prefixMatcher = new PrefixMatcher("test", "Test", emptyPatterns);
        MatchResult result2 = prefixMatcher.match("any text");
        assertEquals(MatchResultEnum.FALSE, result2.result());
        
        ClientNameMatcher clientMatcher = new ClientNameMatcher("test", "Test", emptyPatterns);
        MatchResult result3 = clientMatcher.match("any text");
        assertEquals(MatchResultEnum.FALSE, result3.result());
        
        PeerIdMatcher peerMatcher = new PeerIdMatcher("test", "Test", emptyPatterns);
        MatchResult result4 = peerMatcher.match("any text");
        assertEquals(MatchResultEnum.FALSE, result4.result());
    }

    @Test
    public void testMultiplePatternMatching() {
        List<String> patterns = Arrays.asList("bad", "fake", "malicious");
        
        SubstringMatcher matcher = new SubstringMatcher("test", "Test", patterns);
        
        // Test multiple matches in same string
        MatchResult result1 = matcher.match("bad fake client");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        // Test single match is sufficient
        MatchResult result2 = matcher.match("this is bad");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        MatchResult result3 = matcher.match("fake torrent");
        assertEquals(MatchResultEnum.TRUE, result3.result());
        
        MatchResult result4 = matcher.match("malicious behavior");
        assertEquals(MatchResultEnum.TRUE, result4.result());
        
        // Test no matches
        MatchResult result5 = matcher.match("good client");
        assertEquals(MatchResultEnum.FALSE, result5.result());
    }

    @Test
    public void testSpecialCharactersInPatterns() {
        // Test patterns with special characters
        List<String> specialPatterns = Arrays.asList("client-v1.0", "app[beta]", "test.exe");
        
        SubstringMatcher matcher = new SubstringMatcher("test", "Test", specialPatterns);
        
        MatchResult result1 = matcher.match("my client-v1.0 is here");
        assertEquals(MatchResultEnum.TRUE, result1.result());
        
        MatchResult result2 = matcher.match("running app[beta] version");
        assertEquals(MatchResultEnum.TRUE, result2.result());
        
        MatchResult result3 = matcher.match("found test.exe file");
        assertEquals(MatchResultEnum.TRUE, result3.result());
    }
}