#!/usr/bin/env python3
"""
Script para formatar "xy-plane" em LaTeX: xy-plane -> \(xy\)-plane
"""

import json
import re
from pathlib import Path

def format_xy_plane(text):
    """Formata xy-plane para \(xy\)-plane."""
    if not text or not isinstance(text, str):
        return text
    
    # Substituir xy-plane por \(xy\)-plane
    # Mas não formatar se já está em LaTeX
    pattern = r'\bxy-plane\b'
    
    def repl(match):
        start = match.start()
        before = text[:start]
        
        # Verificar se já está dentro de LaTeX
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)  # Já está dentro de LaTeX
        
        # Não formatar se está logo após \(
        if start >= 2 and before[-2:] == '\\(':
            return match.group(0)
        
        return '\\(xy\\)-plane'
    
    return re.sub(pattern, repl, text)

def process_file(filepath):
    """Processa arquivo."""
    print(f"\n{filepath.name}:")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    count = 0
    for module_key in ['module_1', 'module_2_easy', 'module_2_hard']:
        if module_key not in data:
            continue
        
        for question in data[module_key]:
            modified = False
            
            for field in ['question', 'hint']:
                if field in question:
                    new_val = format_xy_plane(question[field])
                    if new_val != question[field]:
                        question[field] = new_val
                        modified = True
            
            if 'solution' in question and isinstance(question['solution'], list):
                new_solution = []
                for line in question['solution']:
                    new_line = format_xy_plane(line)
                    new_solution.append(new_line)
                    if new_line != line:
                        modified = True
                question['solution'] = new_solution
            
            if modified:
                count += 1
    
    if count > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  ✅ {count} questões modificadas")
    else:
        print(f"  ✓ OK")
    
    return count

if __name__ == '__main__':
    script_dir = Path(__file__).parent
    seed_data_dir = script_dir.parent / 'src' / 'main' / 'resources' / 'seed_data'
    
    exam_files = sorted(seed_data_dir.glob('original_exam_00[1-4].json'))
    
    print(f"📐 Formatando 'xy-plane' em LaTeX...\n")
    
    total = 0
    for exam_file in exam_files:
        try:
            total += process_file(exam_file)
        except Exception as e:
            print(f"  ❌ Erro: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\n✅ Total: {total} questões modificadas")

