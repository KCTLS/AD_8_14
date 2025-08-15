// LoginActivity.kt
package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import com.example.adproject.databinding.ActivityLoginBinding
import com.example.adproject.model.LoginRequest
import com.example.adproject.model.LoginResultVO
import kotlinx.coroutines.launch
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var vb: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vb = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // ✅ 只有在本地认为“已登录”时，才进一步去服务端二次校验；校验失败就清会话并留在登录页
        if (UserSession.isLoggedIn(this)) {
            lifecycleScope.launch {
                val alive = ApiClient.isSessionAlive()
                if (alive) {
                    goExercise()
                    finish()
                    return@launch
                } else {
                    UserSession.clear(this@LoginActivity)
                }
            }
        }

        vb.linkToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        vb.btnLogin.setOnClickListener {
            val email = vb.inputEmail.editText?.text?.toString()?.trim().orEmpty()
            val pwd   = vb.inputPassword.editText?.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val ok = loginAndHandle(email, pwd)
                if (ok) {
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    goExercise()
                    finish()
                }
            }
        }
    }

    private suspend fun loginAndHandle(email: String, pwd: String): Boolean {
        return try {
            val resp: Response<LoginResultVO> = ApiClient.api.login(LoginRequest(email, pwd))
            if (!resp.isSuccessful) {
                Toast.makeText(
                    this,
                    "Login failed(${resp.code()}): ${resp.errorBody()?.string()?.take(200) ?: "No response"}",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            val data = resp.body()
            val success = data?.status.equals("ok", true)
                    && data?.currentAuthority.equals("student", true)

            if (!success) {
                val tip = data?.message ?: data?.msg ?: "Incorrect email or password"
                Toast.makeText(this, tip, Toast.LENGTH_LONG).show()
                return false
            }

            // 登录成功：保存本地会话并写入 token（如有）
            val uid = (data?.userId ?: -1).toInt()
            val uname = data?.userName ?: email.substringBefore("@")
            UserSession.save(this, userId = uid, userName = uname, email = email, token = data?.token)
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Network error：${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun goExercise() {
        startActivity(Intent(this, ExerciseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
