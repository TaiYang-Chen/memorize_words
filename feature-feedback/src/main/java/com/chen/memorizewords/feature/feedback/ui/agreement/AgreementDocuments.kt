package com.chen.memorizewords.feature.feedback.ui.agreement

data class AgreementDocument(
    val title: String,
    val updatedAt: String,
    val effectiveAt: String,
    val version: String,
    val summary: List<String>,
    val sections: List<AgreementSection>
)

data class AgreementSection(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val items: List<AgreementItem> = emptyList()
)

data class AgreementItem(
    val title: String,
    val description: String
)

object AgreementDocuments {
    fun documentFor(type: String): AgreementDocument {
        return when (AgreementTypes.normalize(type)) {
            AgreementTypes.TERMS -> terms()
            else -> privacy()
        }
    }

    private fun privacy(): AgreementDocument {
        return AgreementDocument(
            title = "隐私政策",
            updatedAt = "2026年7月9日",
            effectiveAt = "2026年7月9日",
            version = "v1.0",
            summary = listOf(
                "我们仅围绕账号登录、单词学习、学习记录同步、语音练习、反馈处理和应用更新提供服务所必需的范围处理信息。",
                "录音、悬浮窗、通知、相机、安装未知应用等权限均在具体功能触发时使用；拒绝授权不会影响不依赖该权限的其它功能。",
                "手机号短信验证码仍使用阿里融合认证完成发送、校验和风控；不会使用运营商返回号码直接完成登录。",
                "你可以在系统设置中管理权限，也可以在应用内注销账号或通过意见反馈联系我们处理个人信息请求。"
            ),
            sections = listOf(
                AgreementSection(
                    title = "一、我们如何收集和使用信息",
                    paragraphs = listOf(
                        "炫羲单词会根据你主动使用的功能收集必要信息，包括账号信息、登录状态、学习计划、学习进度、练习记录、收藏与笔记、反馈内容、设备网络状态以及应用更新状态。",
                        "当你使用跟读或口语评分能力时，我们会处理你主动录制的音频及相关练习结果，用于完成评分、展示波形和生成学习记录。"
                    )
                ),
                AgreementSection(
                    title = "二、权限使用说明",
                    paragraphs = listOf(
                        "以下权限仅在对应功能场景下使用。你可以拒绝或在系统设置中关闭授权，关闭后仅影响相关功能。"
                    ),
                    items = listOf(
                        AgreementItem(
                            "录音权限",
                            "用于跟读练习、语音评测和录音波形展示；仅在你进入相关练习并点击录音时触发。关闭后无法使用跟读评分。"
                        ),
                        AgreementItem(
                            "悬浮窗权限",
                            "用于桌面悬浮复习球和悬浮单词卡片；仅在你开启悬浮复习时引导授权。关闭后不会展示悬浮复习入口。"
                        ),
                        AgreementItem(
                            "通知权限",
                            "用于词书下载、词书更新、学习提醒、音频播放和悬浮复习前台通知；关闭后仍可使用主要学习功能，但无法收到系统通知。"
                        ),
                        AgreementItem(
                            "安装未知应用权限",
                            "用于你主动安装应用内更新包；只有在你确认安装更新时跳转系统授权页。关闭后无法通过应用内完成 APK 更新。"
                        ),
                        AgreementItem(
                            "网络状态权限",
                            "用于判断当前网络是否可用、同步学习记录、下载词书和检查更新；不会读取你的通讯内容。"
                        ),
                        AgreementItem(
                            "读取手机状态、切换 WLAN 状态",
                            "用于阿里融合认证短信验证码的号码校验、风险控制和运营商网络能力判断；不会使用运营商返回号码直接完成登录。"
                        ),
                        AgreementItem(
                            "相机权限",
                            "用于个人中心扫码；仅在你打开扫码页面时申请。关闭后无法扫码，但不影响其它学习功能。"
                        ),
                        AgreementItem(
                            "图片读取与文件访问",
                            "用于选择头像、裁剪头像、上传意见反馈截图和读取更新安装包；仅处理你主动选择或下载的文件。"
                        ),
                        AgreementItem(
                            "前台服务权限",
                            "用于下载、同步、音频播放和悬浮复习运行期间保持任务稳定，并通过通知告知运行状态。"
                        )
                    )
                ),
                AgreementSection(
                    title = "三、第三方 SDK 清单",
                    paragraphs = listOf(
                        "为实现登录、短信验证码、图片处理、网络请求、本地存储和基础运行能力，我们会接入以下第三方 SDK 或开源组件。"
                    ),
                    items = listOf(
                        AgreementItem(
                            "阿里融合认证 SDK",
                            "用于手机号短信验证码发送、验证码校验、运营商网络能力判断和风控校验；可能处理手机号、网络状态、设备基础信息和应用包名签名信息。"
                        ),
                        AgreementItem(
                            "微信 OpenSDK、QQ OpenSDK",
                            "用于微信/QQ 登录、授权和账号绑定；会按照平台规则处理授权码、平台账号标识和必要设备信息。"
                        ),
                        AgreementItem(
                            "MMKV、Room",
                            "用于本地保存登录状态、学习配置、学习记录、词书数据和同步队列。"
                        ),
                        AgreementItem(
                            "OkHttp、Retrofit、Moshi、Gson",
                            "用于与服务端通信、同步学习数据、检查更新和解析接口数据。"
                        ),
                        AgreementItem(
                            "Glide、uCrop",
                            "用于头像、反馈图片、助记图片的加载、预览和裁剪处理。"
                        ),
                        AgreementItem(
                            "AndroidX、Hilt、WorkManager、Navigation",
                            "用于应用基础架构、依赖注入、后台任务、页面导航和前台服务能力。"
                        )
                    )
                ),
                AgreementSection(
                    title = "四、信息存储与安全",
                    paragraphs = listOf(
                        "我们会将学习数据、账号状态和本地配置保存在设备本地，并在你登录后按业务需要同步到服务端，以便在多设备或重新安装后恢复学习状态。",
                        "我们会采取访问控制、传输加密、最小化处理和日志脱敏等措施保护你的信息。除法律法规要求或获得你的授权外，我们不会向无关第三方出售或出租你的个人信息。"
                    )
                ),
                AgreementSection(
                    title = "五、你的权利",
                    paragraphs = listOf(
                        "你可以在系统设置中关闭录音、通知、相机、悬浮窗、安装未知应用等权限。关闭权限后，相关功能可能无法继续使用。",
                        "你可以在应用内查看、修改账号资料，管理学习记录和收藏内容，也可以通过注销账号或意见反馈向我们提出查询、更正、删除、撤回授权等请求。"
                    )
                ),
                AgreementSection(
                    title = "六、账号注销",
                    paragraphs = listOf(
                        "你可以在个人中心发起注销账号。注销完成后，我们将按照法律法规要求删除或匿名化处理与你账号相关的个人信息，但法律法规另有规定或为安全审计必须保留的除外。"
                    )
                ),
                AgreementSection(
                    title = "七、未成年人保护",
                    paragraphs = listOf(
                        "若你是未成年人，请在监护人指导下使用本应用。我们不会主动面向未成年人收集与学习服务无关的信息。监护人如需处理未成年人账号信息，可通过意见反馈联系我们。"
                    )
                ),
                AgreementSection(
                    title = "八、联系我们",
                    paragraphs = listOf(
                        "如你对本政策或个人信息处理有任何疑问、投诉或请求，可通过应用内“意见反馈”联系我们。我们会在核验身份后尽快处理。"
                    )
                )
            )
        )
    }

    private fun terms(): AgreementDocument {
        return AgreementDocument(
            title = "用户协议",
            updatedAt = "2026年7月9日",
            effectiveAt = "2026年7月9日",
            version = "v1.0",
            summary = listOf(
                "本协议适用于你使用炫羲单词提供的账号、词书、学习、练习、同步、反馈和应用更新等服务。",
                "请妥善保管账号和验证码，不得以自动化、攻击、作弊或侵犯他人权益的方式使用服务。",
                "应用内更新会在你确认后下载并校验更新包；安装前可能需要跳转系统授权页。"
            ),
            sections = listOf(
                AgreementSection(
                    title = "一、服务说明",
                    paragraphs = listOf(
                        "炫羲单词为用户提供词书学习、复习计划、练习测试、语音跟读、学习记录、账号同步、意见反馈和应用更新等功能。",
                        "我们会持续优化服务内容，并可能根据产品迭代调整、暂停或新增部分功能。"
                    )
                ),
                AgreementSection(
                    title = "二、账号规则",
                    paragraphs = listOf(
                        "你可以通过账号密码、邮箱验证码、手机号短信验证码、微信或 QQ 等方式登录或绑定账号。你应保证提交的信息真实、准确、合法。",
                        "请妥善保管账号、密码、验证码和第三方授权信息。因你主动泄露或保管不当造成的损失，由你自行承担。"
                    )
                ),
                AgreementSection(
                    title = "三、学习服务与内容",
                    paragraphs = listOf(
                        "应用中的词书、例句、释义、学习计划、练习记录和统计结果用于辅助学习，不构成考试、升学、职业资格或其它结果承诺。",
                        "你可以根据个人情况调整学习计划、复习模式、练习方式和悬浮复习设置。"
                    )
                ),
                AgreementSection(
                    title = "四、会员权益",
                    paragraphs = listOf(
                        "如应用提供会员、增值服务或权益功能，具体内容、期限、价格和使用规则以页面展示为准。",
                        "会员权益仅限你本人账号使用，不得转让、出租、售卖或通过非官方渠道获取。"
                    )
                ),
                AgreementSection(
                    title = "五、用户行为规范",
                    paragraphs = listOf(
                        "你不得利用本应用发布违法违规内容、攻击服务、干扰他人使用、绕过权限限制、批量抓取数据、传播恶意程序或侵犯他人合法权益。",
                        "如发现账号存在异常、攻击、作弊、侵权或违法风险，我们有权根据实际情况限制部分功能、暂停服务或配合监管要求处理。"
                    )
                ),
                AgreementSection(
                    title = "六、应用内更新",
                    paragraphs = listOf(
                        "为了修复问题、提升安全性和提供新功能，应用可能展示更新提示。你确认更新后，应用会下载更新包、校验完整性，并引导你通过系统安装器安装。",
                        "如系统要求安装未知应用授权，你可以自行决定是否授予。拒绝授权不会影响当前已安装版本的基本使用，但可能无法获得最新修复。"
                    )
                ),
                AgreementSection(
                    title = "七、服务变更与中断",
                    paragraphs = listOf(
                        "因系统维护、网络异常、第三方服务调整、不可抗力或安全风险处置等原因，部分服务可能出现延迟、中断或不可用。",
                        "我们会尽力保障服务稳定，但不对非我们可控原因导致的服务不可用承担超出法律规定范围的责任。"
                    )
                ),
                AgreementSection(
                    title = "八、免责声明",
                    paragraphs = listOf(
                        "你应根据自身学习情况合理使用本应用。应用提供的学习建议、统计结果和练习反馈仅供参考。",
                        "因你违反本协议、法律法规或第三方平台规则造成的损失，由你自行承担。"
                    )
                ),
                AgreementSection(
                    title = "九、协议更新",
                    paragraphs = listOf(
                        "我们可能根据法律法规、产品功能或运营需要更新本协议。更新后会在应用内展示新的生效日期。",
                        "如你继续使用本应用，即表示你已阅读并同意更新后的协议；如你不同意，可停止使用相关服务。"
                    )
                )
            )
        )
    }
}
