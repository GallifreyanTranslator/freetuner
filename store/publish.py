#!/usr/bin/env python3
"""Publish FreeTuner's store listing, images, and a release bundle to Google Play.

Setup:
    pip install google-auth google-api-python-client
    set PLAY_SERVICE_ACCOUNT_KEY to the path of your service account JSON key

Usage:
    python store/publish.py --track internal
    python store/publish.py --track internal --skip-listing --skip-images   # bundle only
    python store/publish.py --track production --aab path/to/app-release.aab
"""
import argparse
import os
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "com.rstudio.freetuner"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
LANGUAGE = "en-US"

STORE_DIR = Path(__file__).resolve().parent
ASSETS_DIR = STORE_DIR / "assets"
DEFAULT_AAB = STORE_DIR.parent / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"

TITLE = "Tuner: Instrument Tuner"
SHORT_DESCRIPTION = "Precise chromatic tuner, metronome, and chord recognizer — fast, offline, free."
FULL_DESCRIPTION = """FreeTuner is a fast, accurate, offline instrument tuner for guitar, bass, ukulele, violin, viola, cello, mandolin, banjo, and any chromatic instrument.

TUNER
• Real-time pitch detection with a responsive analog-style dial
• Fine ±50 cent scale with a clear in-tune zone
• Adjustable A4 reference pitch (432–446 Hz) for orchestras and alternate tunings
• Adjustable sensitivity and noise filtering for noisy rooms
• Built-in presets: Guitar (Standard, Drop D, Open G, DADGAD), Bass, Ukulele, Violin, Viola, Cello, Mandolin, Banjo, and free Chromatic mode

CHORD RECOGNIZER
• Strum a chord near the microphone and FreeTuner identifies it live
• Recognizes major, minor, 7th, maj7, m7, dim, aug, sus2, and sus4 chords across all 12 roots
• Live pitch-class visualization shows exactly what FreeTuner is hearing

METRONOME
• 40–220 BPM with tap-tempo
• 2–6 beat patterns with accented first beat
• Clear visual beat indicator

TOOLS
• Tuning fork with adjustable note and octave
• Transpose helper for transposing instruments (clarinet, saxophone, trumpet, etc.)

FREE, WITH AN OPTIONAL PRO UPGRADE
• The tuner, metronome, chord recognizer and all standard tunings are free, with no ads
• FreeTuner Pro is a one-time purchase (no subscription) that unlocks extra tunings: half step down, full step down, Drop C, Open D, Open E, 7-string guitar, baritone, 5-string bass, Drop D bass, baritone and low-G ukulele, Double C banjo
• Pro also lets you save your own custom tunings

PRIVACY-FIRST
• Tuning works fully offline — no internet connection needed (only the optional Pro purchase talks to Google Play)
• Microphone audio is processed on-device only and is never recorded, stored, or transmitted
• No accounts, no ads, no analytics, no tracking

Whether you're warming up before a gig, teaching a lesson, or just keeping your practice room in tune, FreeTuner gives you a clean, distraction-free toolkit built for musicians."""

SCREENSHOTS = [
    ASSETS_DIR / "screenshot-1-tuner.png",
    ASSETS_DIR / "screenshot-2-metronome.png",
    ASSETS_DIR / "screenshot-3-tools.png",
    ASSETS_DIR / "screenshot-4-chords.png",
    ASSETS_DIR / "screenshot-5-settings.png",
]
ICON = ASSETS_DIR / "icon-512.png"
FEATURE_GRAPHIC = ASSETS_DIR / "feature-graphic-1024x500.png"


def get_service():
    key_path = os.environ.get("PLAY_SERVICE_ACCOUNT_KEY")
    if not key_path:
        sys.exit("Set PLAY_SERVICE_ACCOUNT_KEY to the path of your service account JSON key.")
    creds = service_account.Credentials.from_service_account_file(key_path, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds)


def update_details(service, edit_id):
    """Ensure defaultLanguage matches LANGUAGE without clobbering contact info."""
    current = service.edits().details().get(packageName=PACKAGE_NAME, editId=edit_id).execute()
    if current.get("defaultLanguage") != LANGUAGE:
        current["defaultLanguage"] = LANGUAGE
        service.edits().details().update(packageName=PACKAGE_NAME, editId=edit_id, body=current).execute()
        print(f"Default language corrected to {LANGUAGE}.")


def update_listing(service, edit_id):
    service.edits().listings().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        language=LANGUAGE,
        body={
            "language": LANGUAGE,
            "title": TITLE,
            "shortDescription": SHORT_DESCRIPTION,
            "fullDescription": FULL_DESCRIPTION,
        },
    ).execute()
    print("Store listing text updated.")


def upload_images(service, edit_id):
    image_sets = {
        "icon": [ICON],
        "featureGraphic": [FEATURE_GRAPHIC],
        "phoneScreenshots": SCREENSHOTS,
    }
    for image_type, paths in image_sets.items():
        service.edits().images().deleteall(
            packageName=PACKAGE_NAME, editId=edit_id, language=LANGUAGE, imageType=image_type,
        ).execute()
        uploaded = 0
        for path in paths:
            if not path.exists():
                print(f"  skip missing {path}")
                continue
            service.edits().images().upload(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                language=LANGUAGE,
                imageType=image_type,
                media_body=MediaFileUpload(str(path), mimetype="image/png"),
            ).execute()
            uploaded += 1
        print(f"Uploaded {uploaded} image(s) for {image_type}.")


def upload_bundle(service, edit_id, aab_path):
    result = service.edits().bundles().upload(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        media_body=MediaFileUpload(str(aab_path), mimetype="application/octet-stream"),
    ).execute()
    version_code = result["versionCode"]
    print(f"Uploaded bundle, versionCode={version_code}")
    return version_code


def assign_track(service, edit_id, track, version_code, release_notes):
    service.edits().tracks().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        track=track,
        body={
            "track": track,
            "releases": [{
                "versionCodes": [str(version_code)],
                "status": "completed",
                "releaseNotes": [{"language": LANGUAGE, "text": release_notes}],
            }],
        },
    ).execute()
    print(f"Assigned versionCode {version_code} to track '{track}'.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--track", default="internal", choices=["internal", "alpha", "beta", "production"])
    parser.add_argument("--aab", default=str(DEFAULT_AAB))
    parser.add_argument("--notes", default="Initial release.")
    parser.add_argument("--skip-listing", action="store_true")
    parser.add_argument("--skip-images", action="store_true")
    parser.add_argument("--skip-bundle", action="store_true")
    args = parser.parse_args()

    service = get_service()
    edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
    edit_id = edit["id"]
    print(f"Opened edit {edit_id}")

    try:
        if not args.skip_listing:
            update_details(service, edit_id)
            update_listing(service, edit_id)
        if not args.skip_images:
            upload_images(service, edit_id)
        if not args.skip_bundle:
            aab_path = Path(args.aab)
            if not aab_path.exists():
                sys.exit(f"AAB not found: {aab_path}")
            version_code = upload_bundle(service, edit_id, aab_path)
            assign_track(service, edit_id, args.track, version_code, args.notes)
    except Exception:
        print("Error occurred -- deleting the draft edit before exiting.")
        service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        raise

    commit = service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print(f"Committed edit {commit['id']}.")


if __name__ == "__main__":
    main()
