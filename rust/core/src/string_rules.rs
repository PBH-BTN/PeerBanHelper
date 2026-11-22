//! Peer ID and client name matchers from the Java RuleParser and matcher classes.

use fancy_regex::Regex;
use serde::Deserialize;
use serde_json::{Value, json};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
enum Outcome {
    False,
    Default,
    True,
}

#[derive(Debug)]
enum Rule {
    Constant(Outcome, Option<&'static str>),
    Matcher(Box<Matcher>),
}

#[derive(Debug, Clone, Copy)]
enum TextMethod {
    StartsWith,
    EndsWith,
    Contains,
    Equals,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "method", rename_all = "SCREAMING_SNAKE_CASE")]
enum MethodConfig {
    StartsWith { content: String },
    EndsWith { content: String },
    Contains { content: String },
    Equals { content: String },
    Regex { content: String },
    Length { min: i32, max: i32 },
}

#[derive(Debug)]
enum MatchMethod {
    Text(TextMethod, String),
    Regex(Regex, String),
    Length { min: i32, max: i32 },
}

#[derive(Debug, Deserialize)]
struct MatcherConfig {
    #[serde(rename = "if")]
    condition: Option<Value>,
    hit: Option<Outcome>,
    miss: Option<Outcome>,
    #[serde(flatten)]
    method: MethodConfig,
}

#[derive(Debug)]
struct Matcher {
    condition: Option<Rule>,
    hit: Option<Outcome>,
    miss: Option<Outcome>,
    method: MatchMethod,
}

#[derive(Debug, thiserror::Error)]
pub enum StringRulesError {
    #[error("malformed string rule: {0}")]
    Json(#[from] serde_json::Error),
    #[error("invalid or unsupported string regular expression: {0}")]
    Regex(#[from] Box<fancy_regex::Error>),
    #[error("string rules must be objects, scalar conditions or null")]
    InvalidShape,
}

#[derive(Debug, Default)]
pub struct StringRules(Vec<Rule>);

#[derive(Debug, PartialEq, Eq)]
pub struct StringRuleMatch {
    pub name: Option<Value>,
    pub comment: Option<Value>,
    pub metadata: String,
}

impl StringRules {
    pub fn parse(rules: &[String]) -> Result<Self, StringRulesError> {
        rules
            .iter()
            .map(|rule| Rule::parse(serde_json::from_str(rule)?))
            .collect::<Result<Vec<_>, _>>()
            .map(Self)
    }

    pub fn check(&self, text: Option<&str>) -> Result<Option<StringRuleMatch>, StringRulesError> {
        let text = text.unwrap_or_default();
        let mut matched = None;
        for rule in &self.0 {
            let (outcome, on_match) = rule.check(text)?;
            match outcome {
                Outcome::False => return Ok(None),
                Outcome::True => matched = on_match,
                Outcome::Default => {}
            }
        }
        Ok(matched)
    }
}

impl Rule {
    fn parse(raw: Value) -> Result<Self, StringRulesError> {
        let (value, key) = match raw {
            Value::Null => (true, None),
            Value::Bool(value) => (value, Some("MATCH_CONDITION_BOOLEAN")),
            Value::Number(number) => {
                // Gson's Number.intValue narrows to a signed 32-bit integer.
                let narrowed = match (number.as_i64(), number.as_u64()) {
                    (Some(value), _) => value as i32,
                    (_, Some(value)) => value as i32,
                    _ => {
                        let int_value = number.as_f64().unwrap_or_default().trunc();
                        int_value.rem_euclid(4_294_967_296.0) as u32 as i32
                    }
                };
                (narrowed != 0, Some("MATCH_CONDITION_BOOLEAN_BY_INTEGER"))
            }
            Value::String(value) => (
                value.eq_ignore_ascii_case("true"),
                Some("MATCH_CONDITION_BOOLEAN_BY_STRING"),
            ),
            Value::Object(_) => return Ok(Self::Matcher(Box::new(Matcher::parse(raw)?))),
            Value::Array(_) => return Err(StringRulesError::InvalidShape),
        };
        let outcome = if value { Outcome::True } else { Outcome::False };
        Ok(Self::Constant(outcome, key))
    }

    fn check(&self, text: &str) -> Result<(Outcome, Option<StringRuleMatch>), StringRulesError> {
        match self {
            Self::Matcher(matcher) => matcher.check(text),
            Self::Constant(outcome, key) => Ok((
                *outcome,
                (*outcome == Outcome::True).then(|| StringRuleMatch {
                    name: None,
                    comment: key.map(|key| json!({"key": key, "params": []})),
                    metadata: String::new(),
                }),
            )),
        }
    }
}

impl Matcher {
    fn parse(raw: Value) -> Result<Self, StringRulesError> {
        let config: MatcherConfig = serde_json::from_value(raw)?;
        let method = match config.method {
            MethodConfig::StartsWith { content } => {
                MatchMethod::Text(TextMethod::StartsWith, content.to_lowercase())
            }
            MethodConfig::EndsWith { content } => {
                MatchMethod::Text(TextMethod::EndsWith, content.to_lowercase())
            }
            MethodConfig::Contains { content } => {
                MatchMethod::Text(TextMethod::Contains, content.to_lowercase())
            }
            MethodConfig::Equals { content } => MatchMethod::Text(TextMethod::Equals, content),
            MethodConfig::Regex { content } => MatchMethod::Regex(
                Regex::new(&format!(r"\A(?:{content})\z")).map_err(Box::new)?,
                content,
            ),
            MethodConfig::Length { min, max } => MatchMethod::Length { min, max },
        };
        Ok(Self {
            condition: config.condition.map(Rule::parse).transpose()?,
            hit: config.hit,
            miss: config.miss,
            method,
        })
    }

    fn check(&self, text: &str) -> Result<(Outcome, Option<StringRuleMatch>), StringRulesError> {
        if let Some(condition) = &self.condition
            && condition.check(text)?.0 == Outcome::False
        {
            // AbstractJsonMatcher yields FALSE here, which vetoes the whole list.
            return Ok((Outcome::False, None));
        }
        let matched = self.method.matches(text)?;
        let outcome = if matched {
            self.hit.unwrap_or(Outcome::True)
        } else {
            self.miss.unwrap_or(Outcome::Default)
        };
        let details = (outcome == Outcome::True).then(|| {
            self.method
                .details(matched, self.hit.is_some(), self.miss.is_some())
        });
        Ok((outcome, details))
    }
}

impl MatchMethod {
    fn matches(&self, text: &str) -> Result<bool, StringRulesError> {
        Ok(match self {
            Self::Text(method, content) => match method {
                TextMethod::StartsWith => text.to_lowercase().starts_with(content),
                TextMethod::EndsWith => text.to_lowercase().ends_with(content),
                TextMethod::Contains => text.to_lowercase().contains(content),
                TextMethod::Equals => equals_ignore_case(text, content),
            },
            Self::Regex(regex, _) => regex.is_match(text).map_err(Box::new)?,
            Self::Length { min, max } => {
                let count = i32::try_from(text.encode_utf16().count()).unwrap_or(i32::MAX);
                (*min..=*max).contains(&count)
            }
        })
    }

    fn details(&self, matched: bool, has_hit: bool, has_miss: bool) -> StringRuleMatch {
        let custom = if matched { has_hit } else { has_miss };
        let (key, argument, metadata, comment) = match self {
            Self::Text(method, content) => {
                let key = match method {
                    TextMethod::StartsWith => "MATCH_STRING_STARTS_WITH",
                    TextMethod::EndsWith => "MATCH_STRING_ENDS_WITH",
                    TextMethod::Contains => "MATCH_STRING_CONTAINS",
                    TextMethod::Equals => "MATCH_STRING_EQUALS",
                };
                let comment = if !custom {
                    content.clone()
                } else if matched || matches!(method, TextMethod::StartsWith) {
                    format!("Hit-{content}")
                } else {
                    format!("Miss-{content}")
                };
                (key, content.clone(), content.clone(), comment)
            }
            Self::Regex(_, pattern) => {
                let comment = if !custom {
                    pattern.clone()
                } else if matched {
                    format!("Hit={pattern}")
                } else {
                    format!("Miss={pattern}")
                };
                (
                    "MATCH_STRING_REGEX",
                    pattern.clone(),
                    pattern.clone(),
                    comment,
                )
            }
            Self::Length { min, max } => {
                let argument = format!("Min-{min}, Max-{max}");
                let comment = if !matched && has_miss {
                    format!("Miss-{argument}")
                } else {
                    format!("Hit-{argument}")
                };
                (
                    "MATCH_STRING_LENGTH",
                    argument,
                    format!("min: {min}, max: {max}"),
                    comment,
                )
            }
        };
        StringRuleMatch {
            name: Some(json!({"key": key, "params": [argument]})),
            comment: Some(json!({"key": key, "params": [comment]})),
            metadata,
        }
    }
}

fn equals_ignore_case(left: &str, right: &str) -> bool {
    if left.is_ascii() && right.is_ascii() {
        return left.eq_ignore_ascii_case(right);
    }
    let mut right = right.chars();
    left.chars().all(|left| {
        right
            .next()
            .is_some_and(|right| simple_fold(left) == simple_fold(right))
    }) && right.next().is_none()
}

fn simple_fold(character: char) -> char {
    if matches!(character, 'İ' | 'ı') {
        return 'i';
    }
    let mut upper = character.to_uppercase();
    let upper_first = upper.next().unwrap_or(character);
    if upper.next().is_none() {
        upper_first.to_lowercase().next().unwrap_or(character)
    } else {
        character.to_lowercase().next().unwrap_or(character)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rules(patterns: &[&str]) -> StringRules {
        StringRules::parse(
            &patterns
                .iter()
                .map(|pattern| (*pattern).into())
                .collect::<Vec<_>>(),
        )
        .unwrap()
    }

    #[test]
    fn last_hit_wins_and_false_vetoes() {
        let positive = r#"{"method":"STARTS_WITH","content":"A"}"#;
        let other = r#"{"method":"CONTAINS","content":"b"}"#;
        let veto = r#"{"method":"EQUALS","content":"aB","hit":"FALSE"}"#;
        assert_eq!(
            rules(&[positive, other])
                .check(Some("Ab"))
                .unwrap()
                .unwrap()
                .metadata,
            "b"
        );
        assert_eq!(
            rules(&[positive, veto, other]).check(Some("Ab")).unwrap(),
            None
        );
    }

    #[test]
    fn conditions_and_explicit_miss_follow_java_outcome_rules() {
        let veto = r#"{"if":false,"method":"LENGTH","min":0,"max":10}"#;
        let fallback = r#"{"method":"ENDS_WITH","content":"!","miss":"TRUE"}"#;
        assert_eq!(rules(&[fallback, veto]).check(None).unwrap(), None);
        let hit = rules(&[fallback]).check(None).unwrap().unwrap();
        assert_eq!(hit.comment.unwrap()["params"][0], "Miss-!");
    }

    #[test]
    fn length_uses_utf16_and_regex_requires_full_match() {
        let length = rules(&[r#"{"method":"LENGTH","min":2,"max":2}"#]);
        let regex = rules(&[r#"{"method":"REGEX","content":"[a-z]+"}"#]);
        assert!(length.check(Some("😀")).unwrap().is_some());
        assert!(regex.check(Some("abc")).unwrap().is_some());
        assert!(regex.check(Some("1abc")).unwrap().is_none());
    }
}
