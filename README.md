# android-deepar-amazon-ivs-sample-integration

To run the example
1. Go to https://developer.deepar.ai, sign up, create the project and the Android app, copy the license key and paste it to MainActivity.java (instead of your_license_key_goes_here string)
2. Follow the Amazon IVS User Guide at https://docs.aws.amazon.com/ivs/latest/userguide/getting-started.html and create a channel.
3. Copy Ingest server and Stream key into MainActivity.java to `INGEST_SERVER` and `STREAM_KEY` variables from the created channel in the Amazon IVS console.
4. Run the app. Watch the live stream in the Amazon IVS console.
