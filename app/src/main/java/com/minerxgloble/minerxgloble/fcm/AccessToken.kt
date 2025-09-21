package com.minerxgloble.minerxgloble.fcm


import android.os.AsyncTask
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.common.collect.Lists
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AccessToken {
    companion object {
        private const val FIREBASE_MESSAGING_SCOPE =
            "https://www.googleapis.com/auth/firebase.messaging"

        fun getAccessTokenAsync(callback: AccessTokenCallback) {
            AccessTokenTask(callback).execute()
        }
    }

    private class AccessTokenTask(private val callback: AccessTokenCallback) :
        AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            return try {
                val jsonString = """
               {
  "type": "service_account",
  "project_id": "minerxgloble",
  "private_key_id": "REDACTED_KEY_ID",
  "private_key": "REDACTED_KEY_START\nREDACTED_KEY_BODY\n/FssQW2AgayzNxDmUMKQOiUaMXY3UKAx3SyXVdh0JHmzdr84FFOtUkYINBVLwVy0\nbKZSJvmmKm2ff4KrUE0L7Mq9zXZ9DfsuB5FSHUJLvJki0PhML/nO9SgE2UoqzUVD\n7ica37fGQfssAbZfhG5mTwVcYgrzzRJ74s0ibFtU6ShP2K8TeSBoq/2Klye2Xb6D\ngDSKeBUATxknObyJe/vNCT8zKRuGUjXigTK/uHUIyxyowhdzOEXqU0zbH1vMDktA\ntJn+r456xUY7RWdtpr2h27xPf/HHK1nj8lnsw8f12yAN8xUocITt5Td1lvJ9iWT7\n6YP/AK6vAgMBAAECggEAAMVbq2MhxeAjTH6Od/H9kM2tEnbkfQMUYcieqGVEnfwT\nx2yfxSkhq3sR7RDHs9Y75P3ZXyGXbRScHeEFlm0oaC1kAgVRQQYKbL+mmyaiyuGZ\nMLj9n5KV1ESwTfX94wgRievbDX2nbtcdap8GDe+IOlu4sJeHEgCmDIRDMHD8uqTs\nL7M8M6jsDeVFxEfETzLTKiIp+c68VHnVlR4yxusYw5KivKf3aF5+srAuaceKggP/\nTpCnxcIfcD6jMem0ohXbQW/0WYmoRufZ71LGPtSgwW4tRJYfJYIyfbuOATfKOKgF\n/aWCPPmo0dzxrURYMSBkB0krStL4Gj8NK0t6MlMPwQKBgQDzKrjGhZA6sEDcXgMy\nVgZS2ZbRNsqq+egp6xMl9wl2FIXqWc7MjoUFUkhTVZKDyI3ZJNdyFKd8NTOWUGmL\n5bmXqcNNxTcurAjkNHQseLIrCSBXTXIxWpA9em4//ZthiGJmZ9Uq6hLIh0Ef+SEf\ngLTns4v+vaXZ8Vk6PtSYMaHU7wKBgQDWDDkQgfKSJqN+XnvE421HKclXn9B3XgdK\neLdWkYB2DeKeRsUDNUUv+yKvIBUCToCNnDiZnDyNTyKpUcCLxJ7HtD3NITtL9zJh\nM7K1A0mIeGj6gUEzXWwBg5TnLYhswmVjFZyHn2JmbUyyFdQP9GVvM/bCQTOeP8vP\nudcefDxCQQKBgQCkzM9tvBeDrvBGaXDBLIwcmlscb4XrWnN99VOE52gCHuajbTo6\naFy+voVF9TjXF5ULFWzuZBEenO/Zb6YYqhieMO+sRXygpPdhsisJ+MLHZ+gDQvmh\neT9IazFNLROhhk0qGjTeYMVaIlCA2tcYAqKYZZb7joxYqLlQQETU4M5NAQKBgARI\nJFAXOWIBEd4yR6mNnx8AT/3HvaTVpbGwroI10OsLpg/ifIhu5V5rWKtGKgVsypeK\nm3s/K9rrzVazwvVIzqBSE7ZpxsTjQge9wAJs+WiYeAki0soTQVjaZ/0j5Qm/7cVI\npmP0JUTFRPZ/B0n2ap0J3hLOuRieUBZsskfy1kXBAoGBAKkv5T4YWPTaeuBKNOKp\nzKxwI4IqfviFnCoSHo2rN2XCi7dWfEnA9QXdukp45WkWRAkbiu01cZW60x3mQbef\nZ+SQt9+SnWiV9Xl0He1IF6C2HJpRQFr7jiV8myw9RVBOekAZhQd2TUox7k9H+GAq\nh1zQYLUFV7MH73F1Z0E0NjLS\nREDACTED_KEY_END\n",
  "client_email": "REDACTED_CLIENT_EMAIL",
  "client_id": "101111869679102443692",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40minerxgloble.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}
            """
                val stream: InputStream =
                    ByteArrayInputStream(jsonString.toByteArray(StandardCharsets.UTF_8))
                val googleCredentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList(FIREBASE_MESSAGING_SCOPE))
                googleCredentials.refreshIfExpired()
                googleCredentials.accessToken.tokenValue
            } catch (e: IOException) {
                Log.e("AccessToken", "Error retrieving access token", e)
                null
            }
        }

        override fun onPostExecute(token: String?) {
            callback.onAccessTokenReceived(token)
        }
    }

    interface AccessTokenCallback {
        fun onAccessTokenReceived(token: String?)
    }
}
