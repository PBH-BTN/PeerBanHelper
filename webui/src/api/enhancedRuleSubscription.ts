import { request } from '@/utils/request'

export interface EnhancedRuleInfo {
  ruleId: string
  enabled: boolean
  ruleName: string
  subUrl: string
  ruleType: string
  lastUpdate: number
  entCount: number
  description?: string
}

export interface EnhancedRuleCreateRequest {
  ruleId: string
  ruleName: string
  ruleType: string
  subUrl: string
  description?: string
  enabled: boolean
}

export interface EnhancedRuleUpdateRequest {
  ruleName?: string
  ruleType?: string
  subUrl?: string
  description?: string
  enabled?: boolean
}

export interface EnhancedRuleLogInfo {
  id: number
  ruleId: string
  updateTime: number
  count: number
  updateType: string
  ruleType: string
  errorMessage?: string
}

export interface RuleType {
  code: string
  name: string
  description: string
}

export interface ApiResponse<T = any> {
  success: boolean
  message: string
  data: T
}

export interface PagedResponse<T = any> {
  results: T[]
  total: number
  page: number
  size: number
}

// Get supported rule types
export function GetEnhancedRuleTypes(): Promise<ApiResponse<RuleType[]>> {
  return request({
    url: '/api/enhanced-sub/rule-types',
    method: 'GET'
  })
}

// Get check interval
export function GetEnhancedRuleCheckInterval(): Promise<ApiResponse<number>> {
  return request({
    url: '/api/enhanced-sub/interval',
    method: 'GET'
  })
}

// Update check interval
export function UpdateEnhancedRuleCheckInterval(checkInterval: number): Promise<ApiResponse> {
  return request({
    url: '/api/enhanced-sub/interval',
    method: 'PATCH',
    data: { checkInterval }
  })
}

// Create enhanced rule subscription
export function CreateEnhancedRule(data: EnhancedRuleCreateRequest): Promise<ApiResponse> {
  return request({
    url: '/api/enhanced-sub/rule',
    method: 'PUT',
    data
  })
}

// Update enhanced rule subscription
export function UpdateEnhancedRule(ruleId: string, data?: EnhancedRuleUpdateRequest): Promise<ApiResponse> {
  if (data) {
    return request({
      url: `/api/enhanced-sub/rule/${ruleId}`,
      method: 'POST',
      data
    })
  } else {
    // Manual update
    return request({
      url: `/api/enhanced-sub/rule/${ruleId}/update`,
      method: 'POST'
    })
  }
}

// Get enhanced rule subscription
export function GetEnhancedRule(ruleId: string): Promise<ApiResponse<EnhancedRuleInfo>> {
  return request({
    url: `/api/enhanced-sub/rule/${ruleId}`,
    method: 'GET'
  })
}

// Delete enhanced rule subscription
export function DeleteEnhancedRule(ruleId: string): Promise<ApiResponse> {
  return request({
    url: `/api/enhanced-sub/rule/${ruleId}`,
    method: 'DELETE'
  })
}

// Toggle enhanced rule subscription enable/disable
export function ToggleEnhancedRuleEnable(ruleId: string, enabled: boolean): Promise<ApiResponse> {
  return request({
    url: `/api/enhanced-sub/rule/${ruleId}`,
    method: 'PATCH',
    data: { enabled }
  })
}

// Get enhanced rule subscription list
export function GetEnhancedRuleList(): Promise<ApiResponse<EnhancedRuleInfo[]>> {
  return request({
    url: '/api/enhanced-sub/rules',
    method: 'GET'
  })
}

// Update all enhanced rule subscriptions
export function UpdateAllEnhancedRules(): Promise<ApiResponse> {
  return request({
    url: '/api/enhanced-sub/rules/update',
    method: 'POST'
  })
}

// Get enhanced rule subscription logs
export function GetEnhancedRuleLogs(params?: {
  ruleId?: string
  page?: number
  size?: number
}): Promise<ApiResponse<PagedResponse<EnhancedRuleLogInfo>>> {
  const url = params?.ruleId 
    ? `/api/enhanced-sub/logs/${params.ruleId}`
    : '/api/enhanced-sub/logs'
  
  return request({
    url,
    method: 'GET',
    params: {
      page: params?.page,
      size: params?.size
    }
  })
}