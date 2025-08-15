package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import com.example.adproject.databinding.ActivityRegisterBinding
import com.example.adproject.model.LoginRequest
import com.example.adproject.model.RegisterRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RegisterActivity : AppCompatActivity() {

    private lateinit var vb: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(vb.root)

        // 性别下拉
        val genders = listOf("male", "female", "other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, genders)
        vb.dropGender.setAdapter(adapter)

        // 已有账号？去登录
        vb.linkToLogin.setOnClickListener { finish() }

        vb.btnRegister.setOnClickListener {
            // 读取并清洗输入
            val name  = vb.inputName.editText?.text?.toString()?.trim().orEmpty()
            val email = vb.inputEmail.editText?.text?.toString()?.trim().orEmpty()
            val pwd   = vb.inputPassword.editText?.text?.toString()?.trim().orEmpty()

            if (name.isEmpty() || email.isEmpty() || pwd.isEmpty()) {
                toast("Please complete all required fields"); return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Invalid email format"); return@setOnClickListener
            }

            // ✅ 修复空安全：先 orEmpty() 再 ifBlank { ... }
            val address   = vb.inputAddress.editText?.text?.toString()?.trim().orEmpty().ifBlank { "N/A" }
            val phone     = vb.inputPhone.editText?.text?.toString()?.trim().orEmpty().ifBlank { "0000000000" }
            val gender    = vb.dropGender.text?.toString()?.trim().orEmpty().ifBlank { "male" }
            val group     = vb.inputGroup.editText?.text?.toString()?.trim().orEmpty().ifBlank { "default" }
            val title     = vb.inputTitle.editText?.text?.toString()?.trim().orEmpty().ifBlank { "student" }
            val signature = vb.inputSignature.editText?.text?.toString()?.trim().orEmpty().ifBlank { "" }

            val tagsInput = vb.inputTags.editText?.text?.toString()?.trim().orEmpty()
            val tags = if (tagsInput.isBlank()) emptyList()
            else tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val req = RegisterRequest(
                address = address,
                email = email,
                gender = gender,
                group = group,
                name = name,
                password = pwd,
                phone = phone,
                signature = signature,
                tags = tags,
                title = title
            )

            setLoading(true)
            lifecycleScope.launch {
                try {
                    // 1) 调注册
                    val regResp = ApiClient.api.register(req)
                    if (!regResp.isSuccessful) {
                        toast("Registration failed (${regResp.code()}): ${regResp.errorBody()?.string()?.take(200) ?: "No response"}")
                        return@launch
                    }
                    val body = regResp.body()

                    // 结合你的后端语义：
                    // code=5 成功；code=4 业务失败(如邮箱重复)；code=0 系统异常
                    when (body?.code) {
                        5 -> {
                            // 2) 注册成功后自动登录
                            val loginResp = ApiClient.api.login(LoginRequest(email, pwd))
                            if (!loginResp.isSuccessful) {
                                toast("Registered, but auto-login failed (${loginResp.code()})")
                                return@launch
                            }
                            val loginData = loginResp.body()
                            val ok = loginData?.status.equals("ok", true)
                                    && loginData?.currentAuthority.equals("student", true)
                            if (!ok) {
                                val tip = loginData?.message ?: loginData?.msg ?: "Registered, but login failed"
                                toast(tip); return@launch
                            }

                            // 3) 保存会话 + token，并进首页
                            val uid = (loginData?.userId ?: -1).toInt()
                            val uname = loginData?.userName ?: name
                            UserSession.save(this@RegisterActivity, uid, uname, email, loginData?.token)
                            loginData?.token?.let { token ->
                                if (token.isNotBlank()) ApiClient.updateAuthToken(token)
                            }

                            toast("Registration and login successful")
                            startActivity(Intent(this@RegisterActivity, ExerciseActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                        }
                        4 -> {
                            // 明确提示：邮箱重复（或其他可恢复的业务错误）
                            val msg = body.msg ?: "Email already exists, please use a different one"
                            toast(msg)
                        }
                        0 -> {
                            toast(body.msg ?: "Server error, please try again later")
                        }
                        else -> {
                            toast(body?.msg ?: "Registration failed")
                        }
                    }
                } catch (e: HttpException) {
                    toast("Network error: HTTP ${e.code()} ${e.message()}")
                } catch (e: Exception) {
                    toast("Network error: ${e.message}")
                } finally {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        vb.btnRegister.isEnabled = !loading
        vb.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
