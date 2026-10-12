package com.example.visualfeedtask.core.network

import java.io.IOException

class HttpException(val statusCode: Int) : IOException("HTTP request failed with status $statusCode")
