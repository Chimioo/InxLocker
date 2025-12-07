import sys
from telethon import TelegramClient
import asyncio
from telethon.sessions import StringSession
from glob import glob
import os

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
""".strip()

def get_caption():
    msg = MSG_TEMPLATE.format(
        commit_message=COMMIT_MESSAGE,
        commit_url=COMMIT_URL,
        another=ANOTHER,
    )
    if len(msg) > 1024:
        return COMMIT_URL
    return msg

async def send_telegram_message(file_patterns):
    files = []
    for pat in file_patterns:
        files.extend(glob(pat))
    async with TelegramClient(StringSession(BOT_CI_SESSION), api_id=API_ID, api_hash=API_HASH) as client:
        await client.start(bot_token=BOT_TOKEN)
        print("[+] Caption: ")
        print(get_caption())
        print("---")
        print("[+] Upload done")
        await client.send_file(
            entity=CHAT_ID,
            file=files,
            parse_mode="markdown",
        )
        await client.send_message(CHAT_ID, get_caption(), parse_mode="markdown")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(1)
    asyncio.run(send_telegram_message(sys.argv[1:]))
    