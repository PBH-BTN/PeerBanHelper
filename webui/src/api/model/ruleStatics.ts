export interface RuleMetric {
  type: string
  hit: number
  query: number
  metadata: {
    rule: string
  }
}

export interface GetRuleMetricsResponse {
  data: RuleMetric[]
  dict: {
    [key: string]: string
  }
}
