<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
    <head>
        <title>YAMJ v3</title>
        <!--Import the header details-->
        <c:import url="../template.jsp">
            <c:param name="sectionName" value="HEAD" />
        </c:import>
    </head>
    <body>
        <!--Import the navigation header-->
        <c:import url="../template.jsp">
            <c:param name="sectionName" value="NAV" />
        </c:import>

        <div id="logo">
            <h2>Artwork profiles</h2>
        </div>

        <table id="headertable" class="hero-unit" style="width: 90%; margin: auto;">
            <tr>
                <th style="vertical-align:top">Name</th>
                <th style="vertical-align:top" class="center">Artwork<br/>Type</th>
                <th style="vertical-align:top" class="center">Width</th>
                <th style="vertical-align:top" class="center">Height</th>
                <th style="vertical-align:top" class="center">Apply To</th>
                <th style="vertical-align:top" class="center">PreProcess</th>
				<th style="vertical-align:top" class="center">Normalize</th>
				<th style="vertical-align:top" class="center">Stretch</th>
				<th style="vertical-align:top" class="center">Reflection</th>
				<th style="vertical-align:top" class="center">Rounded<br/>Corners</th>
				<th/>
            </tr>
            <tbody>
                <c:forEach items="${profilelist}" var="profile" varStatus="row">
                    <tr>
                        <td>${profile.profileName}</td>
                        <td class="center">${profile.artworkType}</td>
                        <td class="center">${profile.width}</td>
                        <td class="center">${profile.height}</td>
                        <td class="center">
                        	<c:if test="${profile.artworkType == 'VIDEOIMAGE'}">
                        	    &nbsp;Episode&nbsp;
							</c:if>                        	 
                        	<c:if test="${profile.artworkType == 'PHOTO'}">
		                        &nbsp;Person&nbsp;
							</c:if>
                        	<c:if test="${profile.artworkType == 'FANART'}">
		   						<c:if test="${profile.applyToMovie == true}">&nbsp;Movie&nbsp;</c:if>
		   						<c:if test="${profile.applyToSeries == true}">&nbsp;Series&nbsp;</c:if>
		   						<c:if test="${profile.applyToSeason == true}">&nbsp;Season&nbsp;</c:if>
		   						<c:if test="${profile.applyToBoxedSet == true}">&nbsp;BoxSet&nbsp;</c:if>
							</c:if>
                        	<c:if test="${profile.artworkType == 'POSTER'}">
		   						<c:if test="${profile.applyToMovie == true}">&nbsp;Movie&nbsp;</c:if>
		   						<c:if test="${profile.applyToSeries == true}">&nbsp;Series&nbsp;</c:if>
		   						<c:if test="${profile.applyToSeason == true}">&nbsp;Season&nbsp;</c:if>
		   						<c:if test="${profile.applyToBoxedSet == true}">&nbsp;BoxSet&nbsp;</c:if>
							</c:if>
                        	<c:if test="${profile.artworkType == 'BANNER'}">
		   						<c:if test="${profile.applyToSeries == true}">&nbsp;Series&nbsp;</c:if>
		   						<c:if test="${profile.applyToSeason == true}">&nbsp;Season&nbsp;</c:if>
		   						<c:if test="${profile.applyToBoxedSet == true}">&nbsp;BoxSet&nbsp;</c:if>
							</c:if>
                        </td>
                        <td class="center">
    						<c:if test="${profile.preProcess == true}">
    						   <img src="${pageContext.request.contextPath}/images/checked.png" alt="enabled" style="width:16px;height:16px"/>
	                        </c:if>
                        </td>
                        <td class="center">
    						<c:if test="${profile.normalize == true}">
    						   <img src="${pageContext.request.contextPath}/images/checked.png" alt="enabled" style="width:16px;height:16px"/>
	                        </c:if>
                        </td>
                        <td class="center">
    						<c:if test="${profile.stretch == true}">
    						   <img src="${pageContext.request.contextPath}/images/checked.png" alt="enabled" style="width:16px;height:16px"/>
	                        </c:if>
                        </td>
                        <td class="center">
    						<c:if test="${profile.reflection == true}">
    						   <img src="${pageContext.request.contextPath}/images/checked.png" alt="enabled" style="width:16px;height:16px"/>
	                        </c:if>
                        </td>
                        <td class="center">
    						<c:if test="${profile.roundedCorners == true}">
    						   <img src="${pageContext.request.contextPath}/images/checked.png" alt="enabled" style="width:16px;height:16px"/>
	                        </c:if>
                        <td class="center" style="width:1%">
                           <span style="white-space:nowrap">
                            <a href="${pageContext.request.contextPath}/profile/edit/${task.name}.html" class="btn info">Edit</a>
                            <a href="${pageContext.request.contextPath}/profile/generate/${profile.id}.html" class="btn info">Regenerate</a>
                           </span>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
            <tfoot>
                <tr>
                    <td colspan="11" class="right">
                        <c:if test="${errorMessage != null}">
                        <span id="messageError" style="align:right">${errorMessage}</span>
                        </c:if>
                        <c:if test="${successMessage != null}">
                        <span id="messageSuccess" style="align:right">${successMessage}</span>
                        </c:if>
                    </td>
                </tr>
            </tfoot>
        </table>
        <p color="green">${successMessage}</p>
        
        <!-- Import the footer -->
        <c:import url="../template.jsp">
            <c:param name="sectionName" value="FOOTER" />
        </c:import>

    </body>
</html>