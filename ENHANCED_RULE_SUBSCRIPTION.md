# Enhanced Rule Subscription

This document describes the Enhanced Rule Subscription feature implemented for PeerBanHelper.

## Overview

The Enhanced Rule Subscription system extends the existing IP blacklist subscription functionality to support multiple types of rules, providing a comprehensive and flexible approach to peer blocking and management.

## Supported Rule Types

### 1. IP Blacklist (`ip_blacklist`)
- **Description**: Blocks peers based on IP addresses
- **Memory Optimization**: Uses `DualIPv4v6AssociativeTries` for efficient memory usage
- **Formats Supported**:
  - Single IP: `192.168.1.1`
  - CIDR notation: `192.168.1.0/24`
  - IP ranges: `192.168.1.1-192.168.1.100`
  - DAT/eMule format: `192.168.1.1,192.168.1.100,200,Comment`

### 2. PeerID (`peer_id`)
- **Description**: Blocks peers based on their PeerID
- **Use Case**: Block specific torrent clients or malicious peer implementations
- **Format**: Exact PeerID matching

### 3. Client Name (`client_name`)
- **Description**: Blocks peers based on their client name/user agent
- **Use Case**: Block specific torrent client versions or fake clients
- **Format**: Exact client name matching

### 4. Substring Match (`substring_match`)
- **Description**: Blocks peers if their client name contains specific substrings
- **Use Case**: Block clients with certain patterns in their names
- **Format**: Substring patterns

### 5. Prefix Match (`prefix_match`)
- **Description**: Blocks peers if their client name starts with specific prefixes
- **Use Case**: Block all versions of a specific client family
- **Format**: Prefix patterns

### 6. Exception List (`exception_list`)
- **Description**: Whitelist that allows specific peers (reverse logic)
- **Use Case**: Ensure trusted peers are never blocked
- **Format**: Same as other types but with allow logic

### 7. Script Engine (`script_engine`)
- **Description**: Custom rule evaluation using scripts
- **Use Case**: Complex rule logic that can't be expressed with simple patterns
- **Format**: Script-based evaluation (future implementation)

## Architecture

### Backend Components

#### Database Schema
- **EnhancedRuleSubInfoEntity**: Stores rule subscription information
- **EnhancedRuleSubLogEntity**: Stores rule update logs

#### Core Classes
- **RuleType**: Enumeration of supported rule types
- **EnhancedRuleSubscriptionModule**: Main module handling rule processing
- **EnhancedRuleMatcher**: Base class for rule matchers
- **Type-specific matchers**: Individual matcher implementations

#### API Endpoints
All endpoints are under `/api/enhanced-sub/` prefix:
- `GET /rule-types` - Get supported rule types
- `GET /interval` - Get check interval
- `PATCH /interval` - Update check interval
- `PUT /rule` - Create rule subscription
- `GET /rule/{id}` - Get rule subscription
- `POST /rule/{id}` - Update rule subscription
- `DELETE /rule/{id}` - Delete rule subscription
- `PATCH /rule/{id}` - Enable/disable rule subscription
- `POST /rule/{id}/update` - Manual rule update
- `GET /rules` - List all rule subscriptions
- `POST /rules/update` - Update all rules
- `GET /logs` - Get update logs

### Frontend Components

#### Vue Components
- **Enhanced Subscription List**: Main management interface
- **Edit Rule Modal**: Rule creation and editing
- **Settings Modal**: Configuration management
- **Log Modal**: Update history viewing

#### Features
- Rule type selection with color-coded indicators
- Form validation for URLs and required fields
- Real-time status updates
- Multilingual support (EN, ZH-CN, ZH-TW)

## Usage

### Creating a Rule Subscription

1. Navigate to Rule Management → Enhanced Rule Subscription
2. Click "Add Enhanced Rule"
3. Fill in the form:
   - **Rule ID**: Unique identifier
   - **Rule Name**: Display name
   - **Rule Type**: Select from dropdown
   - **Subscription URL**: Remote rule source
   - **Description**: Optional description
   - **Enabled**: Enable/disable the rule

### Supported URL Formats

The system supports HTTP/HTTPS URLs that return plain text rule data. The format depends on the rule type:

- **IP rules**: One IP/range per line, with optional comments
- **String rules**: One pattern per line
- **Comments**: Lines starting with `#` are treated as comments

### Automatic Updates

Rules are automatically updated based on the configured check interval. You can:
- Set global check interval in settings
- Manually update individual rules
- Manually update all rules at once

### Monitoring

The system provides comprehensive logging:
- Update timestamps
- Entry counts
- Success/failure status
- Error messages

## Integration

### Module Separation

The Enhanced Rule Subscription system is completely separate from the existing IP blacklist module to ensure:
- No interference with existing functionality
- Independent configuration and management
- Separate API endpoints and database tables

### Memory Efficiency

For IP-based rules, the system uses the same memory-optimized storage (`DualIPv4v6AssociativeTries`) as the original IP blacklist module to ensure efficient memory usage.

### Extensibility

The architecture allows easy addition of new rule types by:
1. Adding new enum values to `RuleType`
2. Implementing new matcher classes
3. Adding parsing logic for the new type

## Configuration

The module configuration is stored in the application's configuration system under the key `enhanced-rule-subscription`. Default settings include:
- Check interval: 24 hours (86400000 ms)
- Ban duration: Configurable per rule type

## Security Considerations

- URL validation ensures only HTTP/HTTPS protocols are accepted
- Rule parsing includes error handling for malformed data
- Database transactions ensure data consistency
- Logging includes error tracking for monitoring

## Future Enhancements

- Script engine rule evaluation
- Advanced rule composition and logic
- Rule performance analytics
- Integration with BTN (BitTorrent Threat Network)
- Rule sharing and community repositories