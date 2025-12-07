import sys
from telethon import TelegramClient
import asyncio
from telethon.sessions import StringSession
from glob import glob
import os
import aiohttp

API_ID = 611335
API_HASH = "d524b414d21f4d37f08684c1df41ac9c"

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = int(os.environ.get("CHAT_ID"))
COMMIT_URL = os.environ.get("COMMIT_URL")
COMMIT_MESSAGE = os.environ.get("COMMIT_MESSAGE")
BOT_CI_SESSION = os.environ.get("BOT_CI_SESSION")
ANOTHER = os.environ.get("ANOTHER")

MSG_TEMPLATE = """

New push to Github
```
{commit_message}
```
by {another}
See commit detail [here]({commit_url})

>{hitokoto}

""".strip()

async def get_hitokoto():
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get('https://v1.hitokoto.cn', timeout=aiohttp.ClientTimeout(total=10)) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    hitokoto = data.get('hitokoto', '')
                    from_who = data.get('from_who', '')
                    from_text = data.get('from', '')
                    
                    if from_who and from_text:
                        return f"{hitokoto} —— 「{from_text}」{from_who}"
                    elif from_text:
                        return f"{hitokoto} —— 「{from_text}」"
                    else:
                        return hitokoto
    except Exception:
        pass
    
    return "我的存在是因为大家存在"

async def send_telegram_message(file_patterns):
    files = []
    for pat in file_patterns:
        files.extend(glob(pat))
    
    hitokoto = await get_hitokoto()
    caption = MSG_TEMPLATE.format(
        hitokoto=hitokoto,
        commit_message=COMMIT_MESSAGE,
        commit_url=COMMIT_URL,
        another=ANOTHER,
    )
    
    async with TelegramClient(StringSession(BOT_CI_SESSION), api_id=API_ID, api_hash=API_HASH) as client:
        await client.start(bot_token=BOT_TOKEN)
        
        if files:
            await client.send_file(entity=CHAT_ID, file=files, caption=caption, parse_mode="markdown")
        else:
            await client.send_message(CHAT_ID, caption, parse_mode="markdown")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(1)
    asyncio.run(send_telegram_message(sys.argv[1:]))