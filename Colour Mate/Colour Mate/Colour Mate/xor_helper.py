def xor_string(data, key=0x5A):
    return "".join(["\\x{:02x}".format(ord(c) ^ key) for c in data])

urls = {
    "GetBUrl": "index.php/Number/",
    "GetGiftUrl": "https://ionicsolutions.in/ColorbyNumberNewColoringBook/getimages.php",
    "GetMainUrl": "http://ionicsolutions.in/ColoringBook/",
    "GetMusicUrl": "https://ionicsolutions.in/ColoringBook/index.php/d2number/Number/audios",
    "GetRestUrl": "index.php/Welcome/",
    "AnimalURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/animals",
    "FlowerURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/flowers",
    "GetExclusive": "https://ionicsolutions.in/ColoringBook/index.php/dhnumber/Number/gimg/c/Exclusive",
    "GetExclusiveBanner": "https://ionicsolutions.in/ColoringBook/index.php/dhnumber/Number/gimg/c/",
    "GetStory": "https://ionicsolutions.in/ColoringBook/index.php/dhnumber/Number/gimg/c/Story",
    "KidURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/kids",
    "LoveURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/love",
    "MandalaURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/mandalas",
    "NatureURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/nature",
    "PeopleURL": "https://ionicsolutions.in/ColoringBook/index.php/Number/gimg/c/people"
}

for name, url in urls.items():
    print(f"{name}: {xor_string(url)}")
