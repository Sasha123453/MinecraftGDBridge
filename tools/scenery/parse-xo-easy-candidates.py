import base64, hashlib, json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
response_file = root / 'work/xo-easy-search.txt'
response = response_file.read_text(encoding='utf-8-sig').strip()
sections = response.split('#')
authors = {}
for item in sections[1].split('|'):
    values = item.split(':')
    if len(values) >= 3:
        authors[values[0]] = {'name': values[1], 'accountId': int(values[2])}

def fields(record):
    values = record.split(':')
    return dict(zip(values[::2], values[1::2]))

def decode(encoded):
    if not encoded:
        return ''
    return base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)).decode('utf-8', errors='replace')

def metadata(record):
    f = fields(record)
    denominator = int(f.get('8', '0') or '0')
    numerator = int(f.get('9', '0') or '0')
    rating = numerator // denominator if denominator else 0
    difficulty = {0: 'NA', 1: 'Easy', 2: 'Normal', 3: 'Hard', 4: 'Harder', 5: 'Insane'}.get(rating, 'Unknown')
    if f.get('17', '') == '1':
        difficulty = {3: 'Easy Demon', 4: 'Medium Demon', 0: 'Hard Demon', 5: 'Insane Demon', 6: 'Extreme Demon'}.get(int(f.get('43', '0') or '0'), 'Demon')
    return {
        'id': int(f['1']), 'name': f.get('2', ''), 'description': decode(f.get('3', '')),
        'playerId': int(f.get('6', '0') or '0'),
        'author': authors.get(f.get('6'), {}).get('name'),
        'accountId': authors.get(f.get('6'), {}).get('accountId'),
        'version': int(f.get('5', '0') or '0'),
        'difficultyDenominator': denominator, 'difficultyNumerator': numerator,
        'communityDifficulty': difficulty,
        'difficultyRatio': rating,
        'demonFlag': f.get('17', '') == '1', 'demonDifficultyRaw': f.get('43'),
        'stars': int(f.get('18', '0') or '0'), 'downloads': int(f.get('10', '0') or '0'),
        'likes': int(f.get('14', '0') or '0'), 'lengthCode': int(f.get('15', '0') or '0'),
        'copyOf': int(f.get('30', '0') or '0'), 'songId': int(f.get('35', '0') or '0'),
        'objectCount': int(f.get('45', '0') or '0'),
        'rawMetadata': {k: v for k, v in f.items() if k != '4'}
    }

candidates = [metadata(record) for record in sections[0].split('|') if record]
original = metadata((root / 'work/xo-official-response.txt').read_text(encoding='utf-8-sig').split('#')[0].strip())
original['author'] = 'KrmaL'
original['accountId'] = 625975
output = {
    'source': 'Official Geometry Dash getGJLevels21.php response saved by parent network executor',
    'query': 'xo easy', 'responseFile': str(response_file),
    'responseSha256': hashlib.sha256(response_file.read_bytes()).hexdigest().upper(),
    'original': original, 'candidates': candidates,
    'recommendedId': 58898913, 'secondaryId': 59223777,
    'recommendationReason': 'jukaras has the most downloads and likes among returned direct copies of original 58825144, shares song 766165, and its description explicitly credits KrmaL. Telpo 58956063 credits an original easier edit by "jakaras"; association with jukaras is an inference from the near-matching name. DragonKnight06 59223777 is the second popular direct copy and has a lower community difficulty ratio.',
    'limitations': ['These are existing community easier edits, not official Easy-star-rated levels: all returned candidates have zero stars and no demon flag.', 'Geometry and gameplay ease must be verified after downloading actual level data.', 'No Nasgubb authorship or exact video match has been established.']
}
target = root / 'outputs/bridge/levels/xo-easy-candidates.json'
target.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf-8')
for item in candidates:
    print(json.dumps({key: item[key] for key in ('id', 'name', 'author', 'difficultyRatio', 'stars', 'downloads', 'likes', 'copyOf', 'songId', 'description')}, ensure_ascii=False))
print('Saved', target)
