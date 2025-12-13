#!/usr/bin/env python3
"""
Script para garantir que TODOS os números estejam formatados em LaTeX.
Corrige números que aparecem sem formatação em question, hint, solution.
"""

import json
import re
from pathlib import Path

def format_number_in_text(text, pos):
    """Verifica se precisa formatar número na posição."""
    if pos == 0 or pos >= len(text):
        return False
    
    # Não formatar se já está em LaTeX
    before = text[:pos]
    opens = before.count('\\(') + before.count('\\[')
    closes = before.count('\\)') + before.count('\\]')
    if opens > closes:
        return False
    
    # Não formatar se está logo após \( ou \[
    if pos > 2 and (text[pos-2:pos] == '\\(' or text[pos-3:pos] == '\\['):
        return False
    
    # Não formatar se está logo antes de \)
    after = text[pos:]
    if len(after) > 2 and (after[:2] == '\\)' or after[:2] == '\\]'):
        return False
    
    return True

def format_numbers_in_text(text):
    """Formata números em texto para LaTeX."""
    if not text or not isinstance(text, str):
        return text
    
    # Não processar campos JSON estruturais
    skip_keywords = ['question_number', 'correct_option', 'version', 'total_questions',
                     'module_1_questions', 'module_2_questions', 'threshold',
                     'duration_minutes', 'coords', 'xRange', 'yRange', 'tickStep',
                     'through', 'label', 'position', 'showGrid', 'showAxes', 'showTicks']
    if any(kw in text for kw in skip_keywords):
        return text
    
    # Padrão para números: inteiros, decimais, com vírgulas
    # Não capturar números que já estão em LaTeX ou em arrays/tables
    pattern = r'\b(\d{1,3}(?:,\d{3})*(?:\.\d+)?|\d+\.\d+|\d+)\b'
    
    def should_format(match):
        num = match.group(0)
        start = match.start()
        
        # Verificar se está dentro de LaTeX
        before = text[:start]
        opens = before.count('\\(') + before.count('\\[')
        closes = before.count('\\)') + before.count('\\]')
        if opens > closes:
            return False
        
        # Verificar contexto: não formatar se está logo após \( ou \[
        if start >= 2:
            if text[start-2:start] == '\\(' or (start >= 3 and text[start-3:start] == '\\['):
                return False
        
        # Verificar se está logo antes de \)
        end = match.end()
        if end < len(text):
            if text[end:end+2] == '\\)' or text[end:end+2] == '\\]':
                return False
        
        # Verificar se está dentro de um array LaTeX
        if '\\begin{' in before or '\\[' in before:
            last_array = before.rfind('\\begin{')
            last_display = before.rfind('\\[')
            last_start = max(last_array, last_display)
            if last_start > 0:
                # Verificar se o array foi fechado
                after_array = text[last_start:]
                if '\\end{' not in after_array[:after_array.find(num)] and '\\]' not in after_array[:after_array.find(num)]:
                    return False
        
        return True
    
    # Encontrar todas as ocorrências
    matches = list(re.finditer(pattern, text))
    
    if not matches:
        return text
    
    # Construir resultado de trás para frente
    result = text
    for match in reversed(matches):
        if should_format(match):
            num = match.group(0)
            start, end = match.span()
            # Formatar número: vírgulas viram {,} em LaTeX
            formatted_num = num.replace(',', '{,}')
            result = result[:start] + f'\\({formatted_num}\\)' + result[end:]
    
    return result

def process_question(question):
    """Processa uma questão."""
    modified = False
    
    # Question
    if 'question' in question:
        new_q = format_numbers_in_text(question['question'])
        if new_q != question['question']:
            question['question'] = new_q
            modified = True
    
    # Hint
    if 'hint' in question:
        new_h = format_numbers_in_text(question['hint'])
        if new_h != question['hint']:
            question['hint'] = new_h
            modified = True
    
    # Solution
    if 'solution' in question and isinstance(question['solution'], list):
        new_solution = []
        for line in question['solution']:
            new_line = format_numbers_in_text(line)
            new_solution.append(new_line)
            if new_line != line:
                modified = True
        question['solution'] = new_solution
    
    # Options - apenas se não estiver em LaTeX já
    if 'options' in question and isinstance(question['options'], dict):
        for key, value in question['options'].items():
            if isinstance(value, str) and '\\[' not in value and '\\begin{' not in value:
                new_val = format_numbers_in_text(value)
                if new_val != value:
                    question['options'][key] = new_val
                    modified = True
    
    return modified

def process_file(filepath):
    """Processa arquivo."""
    print(f"\n{'='*60}")
    print(f"Processando {filepath.name}...")
    
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
    
    print(f"📝 Formatando números em {len(exam_files)} arquivos...")
    
    total_all = 0
    for exam_file in exam_files:
        try:
            total_all += process_file(exam_file)
        except Exception as e:
            print(f"  ❌ Erro: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\n{'='*60}")
    print(f"✅ Total: {total_all} questões modificadas")
    print(f"{'='*60}\n")

