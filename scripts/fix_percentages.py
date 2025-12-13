#!/usr/bin/env python3
"""
Script para corrigir formatação de percentuais: \(75\)% -> \(75\%\)
"""

import json
import re
from pathlib import Path

def fix_percentages(text):
    """Corrige percentuais formatados incorretamente."""
    if not text or not isinstance(text, str):
        return text
    
    # Padrão: \(número\)% -> \(número\%\)
    # Procurar por \(número\)% e substituir por \(número\%\)
    # Usar replace simples primeiro
    text = re.sub(r'\\(\\((\d+(?:,\d{3})*(?:\.\d+)?)\\)\\)%', r'\\(\1\\)%', text)
    
    return text
    
    return re.sub(pattern, replace, text)

def process_file(filepath):
    """Processa um arquivo."""
    print(f"Processando {filepath.name}...")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    modified = 0
    
    for module_key in ['module_1', 'module_2_easy', 'module_2_hard']:
        if module_key not in data:
            continue
        
        for question in data[module_key]:
            # Question
            if 'question' in question:
                new_q = fix_percentages(question['question'])
                if new_q != question['question']:
                    question['question'] = new_q
                    modified += 1
            
            # Hint
            if 'hint' in question:
                new_h = fix_percentages(question['hint'])
                if new_h != question['hint']:
                    question['hint'] = new_h
                    modified += 1
            
            # Solution
            if 'solution' in question and isinstance(question['solution'], list):
                for i, line in enumerate(question['solution']):
                    new_line = fix_percentages(line)
                    if new_line != line:
                        question['solution'][i] = new_line
                        modified += 1
    
    if modified > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  ✓ {modified} ocorrências corrigidas")
    else:
        print(f"  ℹ Nenhuma correção necessária")
    
    return modified

if __name__ == '__main__':
    script_dir = Path(__file__).parent
    seed_data_dir = script_dir.parent / 'src' / 'main' / 'resources' / 'seed_data'
    
    exam_files = sorted(seed_data_dir.glob('original_exam_00[1-4].json'))
    
    total = 0
    for exam_file in exam_files:
        total += process_file(exam_file)
    
    print(f"\n✅ Total: {total} correções de percentuais")

