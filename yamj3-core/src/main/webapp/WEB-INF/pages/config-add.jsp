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
            <h2>Add Configuration Entry</h2>
        </div>
        <p id="message">Enter the property configuration key and value</p>
        <form:form method="POST" commandName="config" action="${pageContext.request.contextPath}/config/add/process.html">
            <table id="headertable">
                <tr>
                    <td class="right">Key:</td>
                    <td><form:input path="key"></form:input></td>
                    </tr>
                    <tr>
                        <td class="right">Value:</td>
                        <td><form:input path="value"></form:input></td>
                    </tr>
                    <tr>
                        <td colspan="2" class="center"><input value="Add" type="submit"></td>
                    </tr>
                </table>
        </form:form>

        <p><a href="${pageContext.request.contextPath}/index.html">Home page</a></p>
    </body>
</html>