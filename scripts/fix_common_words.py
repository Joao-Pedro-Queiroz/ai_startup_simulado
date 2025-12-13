#!/usr/bin/env python3
"""
Script para remover \overline{} de palavras comuns
"""

import json
from pathlib import Path

seed_data_dir = Path(__file__).parent.parent / 'src' / 'main' / 'resources' / 'seed_data'

# Palavras comuns que não devem ter \overline
common_words = [
    'the', 'with', 'of', 'one', 'is', 'has', 'and', 'a', 'an', 'in',
    'to', 'for', 'at', 'by', 'are', 'was', 'were', 'have', 'had',
    'will', 'would', 'can', 'could', 'it', 'but', 'on', 'this', 'that',
    'these', 'those'
]

def fix_text(text):
    """Remove \overline{} de palavras comuns em um texto."""
    if not text or not isinstance(text, str):
        return text
    
    result = text
    for word in common_words:
        # Padrão: \(\overline{word}\)
        pattern = f'\\(\\\\overline{{{word}}}\\)'
        result = result.replace(pattern, word)
    
    return result

def process_file(filepath):
    """Processa arquivo JSON."""
    print(f"\n{filepath.name}:")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    count = 0
    for module_key in ['module_1', 'module_2_easy', 'module_2_hard']:
        if module_key not in data:
            continue
        
        for question in data[module_key]:
            for field in ['question', 'hint']:
                if field in question:
                    new_val = fix_text(question[field])
                    if new_val != question[field]:
                        question[field] = new_val
                        count += 1
            
            if 'solution' in question and isinstance(question['solution'], list):
                new_solution = []
                for line in question['solution']:
                    new_line = fix_text(line)
                    new_solution.append(new_line)
                    if new_line != line:
                        count += 1
                question['solution'] = new_solution
    
    if count > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  ✅ {count} campos modificados")
    else:
        print(f"  ✓ OK")
    
    return count

if __name__ == '__main__':
    exam_files = sorted(seed_data_dir.glob('original_exam_00[1-4].json'))
    
    print("🔧 Removendo \\overline{} de palavras comuns...\n")
    
    total = 0
    for exam_file in exam_files:
        try:
            total += process_file(exam_file)
        except Exception as e:
            print(f"  ❌ Erro: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\n✅ Total: {total} campos modificados")

