#!/bin/bash

# Script wrapper para importar simulados originais
# Carrega automaticamente a variável de ambiente do .env-config.sh

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"

# Carregar variáveis de ambiente do arquivo .env-config.sh se existir
if [ -f "$PROJECT_ROOT/.env-config.sh" ]; then
    echo "📋 Carregando variáveis de ambiente de .env-config.sh..."
    source "$PROJECT_ROOT/.env-config.sh"
fi

# Verificar se a variável está configurada
if [ -z "$mongo_felps" ]; then
    echo "❌ ERRO: Variável de ambiente mongo_felps não configurada!"
    echo ""
    echo "Configure com uma das opções:"
    echo "  1. source $PROJECT_ROOT/.env-config.sh"
    echo "  2. export mongo_felps=\"sua_uri_mongodb\""
    echo ""
    exit 1
fi

# Executar o script de import
echo "🚀 Executando import de simulados originais..."
npm run import

