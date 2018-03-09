var lastResult;

$(document).ready(function() {
    
    $('#keywords').jQCloud([], {
    	  width: 500,
    	  height: 350
	});
    
    /**
     * Get Tag data
     */
    var getWordData = function() {
    	$.ajax({
    		url: 'api/words'+(group ? '?group='+group : ''),
    		type: 'GET',
    		success: function(data) {
    			viewTimer = null;
    			var words = [];
    			data.forEach(function(d) {words.push({text: d.name, weight: d.value})});
//    			for (var key in data) {
//    				words.push({text: key, weight: data[key]});
//    			}
    			$('#keywords').jQCloud('update', words);
    			console.log('data', data);
        	}
    	});
    }
    getWordData();
//    setInterval(getWordData,5*60*1000);
    setInterval(getWordData,10*1000);

    
});
