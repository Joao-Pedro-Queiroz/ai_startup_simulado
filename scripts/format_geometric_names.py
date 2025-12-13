#!/usr/bin/env python3
"""
Script para formatar nomes de figuras geométricas e lados em LaTeX.
Formata: triangle ABC, parallelogram ABCD, side AB, etc.
"""

import json
import re
from pathlib import Path

def format_geometric_names(text):
    """Formata nomes de figuras geométricas e lados em LaTeX."""
    if not text or not isinstance(text, str):
        return text
    
    # Não processar se é array LaTeX
    if '\\begin{' in text or ('\\[' in text and '\\]' in text):
        return text
    
    result = text
    
    # Padrões para formatar:
    # 1. triangle ABC -> \triangle ABC (dentro de \(...\))
    # Mas não formatar "a triangle" ou "the triangle" - só quando menciona nome específico
    pattern1 = r'\btriangle ([A-Z]{2,})\b'
    def repl_triangle(match):
        name = match.group(1)
        # Verificar se já está em LaTeX
        start = match.start()
        before = text[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)  # Já está dentro de LaTeX
        # Não formatar se está em contexto genérico como "a triangle" ou "the triangle"
        # Só formatar se há um nome específico (ABC, DEF, etc)
        return f'\\(\\triangle {name}\\)'
    result = re.sub(pattern1, repl_triangle, result)
    
    # 1b. "right triangle ABC" -> "right \\(\\triangle ABC\\)"
    pattern1b = r'\bright triangle ([A-Z]{2,})\b'
    def repl_right_triangle(match):
        name = match.group(1)
        start = match.start()
        before = result[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        return f'right \\(\\triangle {name}\\)'
    result = re.sub(pattern1b, repl_right_triangle, result)
    
    # 2. parallelogram ABCD -> parallelogram \\(ABCD\\)
    pattern2 = r'\bparallelogram ([A-Z]{2,})\b'
    def repl_parallelogram(match):
        name = match.group(1)
        start = match.start()
        before = result[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        return f'parallelogram \\({name}\\)'
    result = re.sub(pattern2, repl_parallelogram, result)
    
    # 3. rectangle ABCD -> rectangle \\(ABCD\\)
    pattern3 = r'\brectangle ([A-Z]{2,})\b'
    def repl_rectangle(match):
        name = match.group(1)
        start = match.start()
        before = result[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        return f'rectangle \\({name}\\)'
    result = re.sub(pattern3, repl_rectangle, result)
    
    # 4. side AB, side BC, etc -> side \\(AB\\), side \\(BC\\)
    # Mas cuidado: não formatar se já está em expressão matemática
    pattern4 = r'\bside ([A-Z]{2,})\b'
    def repl_side(match):
        name = match.group(1)
        start = match.start()
        before = result[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        # Verificar contexto: se está logo após um = ou outro símbolo matemático, pode já estar em LaTeX
        if start > 0:
            prev_chars = before[-5:] if len(before) >= 5 else before
            if any(c in prev_chars for c in ['=', '(', '\\']):
                # Pode estar em contexto matemático, verificar melhor
                if '\\(' in prev_chars[-3:]:
                    return match.group(0)
        return f'side \\(\\overline{{{name}}}\\)'
    result = re.sub(pattern4, repl_side, result)
    
    # 5. Lados mencionados sozinhos: AB, BC, AC (quando não estão em LaTeX)
    # Mas só formatar se aparecerem como palavras completas (não dentro de outras palavras)
    pattern5 = r'(?<![A-Z])\b([A-Z]{2,})\b(?![A-Z])'
    def repl_side_alone(match):
        name = match.group(1)
        # Só formatar se for nome de lado comum (2-4 letras)
        if len(name) < 2 or len(name) > 4:
            return match.group(0)
        
        start = match.start()
        before = result[:start]
        after = result[match.end():]
        
        # Não formatar se já está em LaTeX
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        
        # Não formatar se está logo após \(
        if start >= 2 and before[-2:] == '\\(':
            return match.group(0)
        
        # Não formatar se está logo antes de \)
        if len(after) >= 2 and after[:2] == '\\)':
            return match.group(0)
        
        # Não formatar se está em contexto não geométrico (ex: "AB" como resposta)
        # Verificar contexto antes e depois
        context_before = before[-20:] if len(before) >= 20 else before
        context_after = after[:20] if len(after) >= 20 else after
        
        # Se aparece em contexto geométrico (triangle, side, angle, etc)
        geometric_context = re.search(r'(triangle|side|angle|parallelogram|rectangle|perimeter|area|length|width|height)\s+[A-Z]*$', context_before, re.I)
        geometric_after = re.search(r'^\s*(is|has|equals|=|are)', context_after, re.I)
        
        # Se está em contexto claramente não geométrico, não formatar
        if not geometric_context and not geometric_after:
            # Verificar se faz sentido formatar (não formatar se está em lista ou enumeração)
            if re.search(r'^[A-D]\)', context_after.strip()):
                return match.group(0)
        
        return f'\\(\\overline{{{name}}}\\)'
    
    # Aplicar apenas em contexto geométrico para evitar falsos positivos
    # Buscar padrões como "side AB", "AB =", "AB is", etc.
    geometric_pattern = r'(side|length of|width of|height of|perimeter of|area of|triangle|parallelogram|rectangle)\s+([A-Z]{2,4})(?=\s|$|,|\.)'
    def repl_geometric_side(match):
        prefix = match.group(1)
        name = match.group(2)
        start = match.start()
        before = result[:start]
        opens = before.count('\\(')
        closes = before.count('\\)')
        if opens > closes:
            return match.group(0)
        return f'{prefix} \\(\\overline{{{name}}}\\)'
    result = re.sub(geometric_pattern, repl_geometric_side, result, flags=re.IGNORECASE)
    
    return result

def process_question(question):
    """Processa uma questão."""
    modified = False
    
    for field in ['question', 'hint']:
        if field in question:
            new_val = format_geometric_names(question[field])
            if new_val != question[field]:
                question[field] = new_val
                modified = True
    
    if 'solution' in question and isinstance(question['solution'], list):
        new_solution = []
        for line in question['solution']:
            new_line = format_geometric_names(line)
            new_solution.append(new_line)
            if new_line != line:
                modified = True
        question['solution'] = new_solution
    
    return modified

def process_file(filepath):
    """Processa arquivo."""
    print(f"\n{filepath.name}:")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    total = 0
    for module_key in ['module_1', 'module_2_easy', 'module_2_hard']:
        if module_key not in data:
            continue
        
        count = 0
        for question in data[module_key]:
            if process_question(question):
                count += 1
                total += 1
        
        if count > 0:
            print(f"  {module_key}: {count} questões")
    
    if total > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  ✅ {total} questões modificadas")
    else:
        print(f"  ℹ Sem alterações")
    
    return total

if __name__ == '__main__':
    script_dir = Path(__file__).parent
    seed_data_dir = script_dir.parent / 'src' / 'main' / 'resources' / 'seed_data'
    
    exam_files = sorted(seed_data_dir.glob('original_exam_00[1-4].json'))
    
    print(f"📐 Formatando nomes de figuras geométricas...\n")
    
    total_all = 0
    for exam_file in exam_files:
        try:
            total_all += process_file(exam_file)
        except Exception as e:
            print(f"  ❌ Erro: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\n✅ Total: {total_all} questões modificadas")

