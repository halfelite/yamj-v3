<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html>
    <head>
        <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/yamj-style.css">
        <link rel="shortcut icon" type="image/x-icon" href="${pageContext.request.contextPath}/favicon.ico" />
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>YAMJ v3</title>
    </head>
    <body>
        <div id="logo">
            <h1>Yet Another Movie Jukebox</h1>
            <h2>Edit Configuration</h2>
        </div>
        <p id="message">Enter the new value required</p>
        <form:form method="POST" commandName="config" action="${pageContext.request.contextPath}/config/edit/${config.key}.html">
            <table id="headertable">
                <tr>
                    <td class="right">Key:</td>
                    <td>${config.key}</td>
                    </tr>
                    <tr>
                        <td class="right">Old Value:</td>
                        <td>${config.value}</td>
                    </tr>
                    <tr>
                        <td class="right">New Value:</td>
                        <td><form:input path="value"></form:input></td>
                    </tr>
                    <tr>
                        <td colspan="2" class="center"><input value="Update" type="submit"></td>
                    </tr>
                </table>
        </form:form>

        <p><a href="${pageContext.request.contextPath}/index.html">Home page</a></p>
    </body>
</html>