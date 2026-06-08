#!/bin/bash

# Load keys manually
GEMINI_API_KEY=$(grep GEMINI_API_KEY local.defaults.properties | cut -d'=' -f2)
GROQ_API_KEY=$(grep GROQ_API_KEY local.defaults.properties | cut -d'=' -f2)
MISTRAL_API_KEY=$(grep MISTRAL_API_KEY local.defaults.properties | cut -d'=' -f2)
OPENROUTER_API_KEY=$(grep OPENROUTER_API_KEY local.defaults.properties | cut -d'=' -f2)
SAMBANOVA_API_KEY=$(grep SAMBANOVA_API_KEY local.defaults.properties | cut -d'=' -f2)
DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY local.defaults.properties | cut -d'=' -f2)
COHERE_API_KEY=$(grep COHERE_API_KEY local.defaults.properties | cut -d'=' -f2)

echo "DEBUG: MISTRAL_API_KEY=${MISTRAL_API_KEY:0:5}...     nnnnnm  "

echo "Testing Mistral..."
curl -s -X POST "https://api.mistral.ai/v1/chat/completions" \
  -H "Authorization: Bearer $MISTRAL_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "mistral-small-latest",
    "messages": [{"role": "user", "content": "Say hello"}]
  }' | jq -c '.choices[0].message.content'

echo -e "\nTesting Groq..."
curl -s -X POST "https://api.groq.com/openai/v1/chat/completions" \
  -H "Authorization: Bearer $GROQ_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-3.3-70b-versatile",
    "messages": [{"role": "user", "content": "Say hello"}]
  }' | jq -c '.'

echo -e "\nTesting Gemini..."
curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "contents": [{
      "parts": [{"text": "Say hello"}]
    }]
  }' | jq -c '.'

echo -e "\nTesting OpenRouter..."
curl -s -X POST "https://openrouter.ai/api/v1/chat/completions" \
  -H "Authorization: Bearer $OPENROUTER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "meta-llama/llama-3.2-3b-instruct:free",
    "messages": [{"role": "user", "content": "Say hello"}]
  }' | jq -c '.'

echo -e "\nTesting SambaNova..."
curl -s -X POST "https://api.sambanova.ai/v1/chat/completions" \
  -H "Authorization: Bearer $SAMBANOVA_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Meta-Llama-3.3-70B-Instruct",
    "messages": [{"role": "user", "content": "Say hello"}]
  }' | jq -c '.'

echo -e "\nTesting DeepSeek..."
curl -s -X POST "https://api.deepseek.com/chat/completions" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Say hello"}]
  }' | jq -c '.'

echo -e "\nTesting Cohere..."
curl -s -X POST "https://api.cohere.ai/v1/chat" \
  -H "Authorization: Bearer $COHERE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "command-r-08-2024",
    "message": "Say hello"
  }' | jq -c '.'
